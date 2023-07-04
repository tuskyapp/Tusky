/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.timeline.viewmodel

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.components.timeline.TimelineKind
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Remote mediator for accessing timelines that are not backed by the database. */
@OptIn(ExperimentalPagingApi::class)
class NetworkTimelineRemoteMediator(
    private val viewModelScope: CoroutineScope,
    private val api: MastodonApi,
    accountManager: AccountManager,
    private val factory: InvalidatingPagingSourceFactory<String, Status>,
    private val pageCache: PageCache,
    private val timelineKind: TimelineKind
) : RemoteMediator<String, Status>() {

    private val activeAccount = accountManager.activeAccount!!

    override suspend fun load(loadType: LoadType, state: PagingState<String, Status>): MediatorResult {
        if (!activeAccount.isLoggedIn()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        return try {
            val key = when (loadType) {
                LoadType.REFRESH -> {
                    // Find the closest page to the current position
                    val itemKey = state.anchorPosition?.let { state.closestItemToPosition(it) }?.id
                    itemKey?.let { ik ->
                        val pageContainingItem = pageCache.floorEntry(ik)
                            ?: throw java.lang.IllegalStateException("$itemKey not found in the pageCache page")

                        // Double check the item appears in the page
                        if (BuildConfig.DEBUG) {
                            pageContainingItem.value.data.find { it.id == itemKey }
                                ?: throw java.lang.IllegalStateException("$itemKey not found in returned page")
                        }

                        // The desired key is the prevKey of the page immediately before this one
                        pageCache.lowerEntry(pageContainingItem.value.data.last().id)?.value?.prevKey
                    }
                }
                LoadType.APPEND -> {
                    pageCache.firstEntry()?.value?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.PREPEND -> {
                    pageCache.lastEntry()?.value?.prevKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
            }

            Log.d(TAG, "- load(), type = $loadType, key = $key")

            val response = fetchStatusPageByKind(loadType, key, state.config.initialLoadSize)
            var page = Page.tryFrom(response).getOrElse { return MediatorResult.Error(it) }

            // If doing a refresh with a known key Paging3 wants you to load "around" the requested
            // key, so that it can show the item with the key in the view as well as context before
            // and after it. If you don't do this state.anchorPosition can get in to a weird state
            // where it starts picking anchorPositions in freshly loaded pages, and the list
            // repeatedly jumps up as new content is loaded with the prepend operations that occur
            // after a refresh.
            //
            // To ensure that the first page loaded after a refresh is big enough that this can't
            // happen load the page immediately before and the page immediately after as well,
            // and merge the three of them in to one large page.
            if (loadType == LoadType.REFRESH && key != null) {
                Log.d(TAG, "  Refresh with non-null key, creating huge page")
                val prevPageJob = viewModelScope.async {
                    page.prevKey?.let { key ->
                        fetchStatusPageByKind(LoadType.PREPEND, key, state.config.initialLoadSize)
                    }
                }
                val nextPageJob = viewModelScope.async {
                    page.nextKey?.let { key ->
                        fetchStatusPageByKind(LoadType.APPEND, key, state.config.initialLoadSize)
                    }
                }
                val prevPage = prevPageJob.await()
                    ?.let { Page.tryFrom(it).getOrElse { return MediatorResult.Error(it) } }
                val nextPage = nextPageJob.await()
                    ?.let { Page.tryFrom(it).getOrElse { return MediatorResult.Error(it) } }
                Log.d(TAG, "    prevPage: $prevPage")
                Log.d(TAG, "     midPage: $page")
                Log.d(TAG, "    nextPage: $nextPage")
                page = page.merge(prevPage, nextPage)
            }

            if (BuildConfig.DEBUG && loadType == LoadType.REFRESH) {
                // Verify page contains the expected key
                state.anchorPosition?.let { state.closestItemToPosition(it) }?.id?.let { itemId ->
                    page.data.find { it.id == itemId }
                        ?: throw IllegalStateException("Fetched page with $key, it does not contain $itemId")
                }
            }

            val endOfPaginationReached = page.data.isEmpty()
            if (!endOfPaginationReached) {
                synchronized(pageCache) {
                    if (loadType == LoadType.REFRESH) {
                        pageCache.clear()
                    }

                    pageCache.upsert(page)
                    Log.d(
                        TAG,
                        "  Page $loadType complete for $timelineKind, now got ${pageCache.size} pages"
                    )
                    pageCache.debug()
                }
                Log.d(TAG, "  Invalidating paging source")
                factory.invalidate()
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }

    @Throws(IOException::class, HttpException::class)
    private suspend fun fetchStatusPageByKind(loadType: LoadType, key: String?, loadSize: Int): Response<List<Status>> {
        val (maxId, minId) = when (loadType) {
            // When refreshing fetch a page of statuses that are immediately *newer* than the key
            // This is so that the user's reading position is not lost.
            LoadType.REFRESH -> Pair(null, key)
            // When appending fetch a page of statuses that are immediately *older* than the key
            LoadType.APPEND -> Pair(key, null)
            // When prepending fetch a page of statuses that are immediately *newer* than the key
            LoadType.PREPEND -> Pair(null, key)
        }

        return when (timelineKind) {
            TimelineKind.Bookmarks -> api.bookmarks(maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.Favourites -> api.favourites(maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.Home -> api.homeTimeline(maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.PublicFederated -> api.publicTimeline(local = false, maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.PublicLocal -> api.publicTimeline(local = true, maxId = maxId, minId = minId, limit = loadSize)
            is TimelineKind.Tag -> {
                val firstHashtag = timelineKind.tags.first()
                val additionalHashtags = timelineKind.tags.subList(1, timelineKind.tags.size)
                api.hashtagTimeline(firstHashtag, additionalHashtags, null, maxId = maxId, minId = minId, limit = loadSize)
            }
            is TimelineKind.User.Pinned -> api.accountStatuses(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = null,
                onlyMedia = null,
                pinned = true
            )
            is TimelineKind.User.Posts -> api.accountStatuses(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = true,
                onlyMedia = null,
                pinned = null
            )
            is TimelineKind.User.Replies -> api.accountStatuses(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = null,
                onlyMedia = null,
                pinned = null
            )
            is TimelineKind.UserList -> api.listTimeline(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize
            )
        }
    }

    companion object {
        private const val TAG = "NetworkTimelineRemoteMediator"
    }
}
