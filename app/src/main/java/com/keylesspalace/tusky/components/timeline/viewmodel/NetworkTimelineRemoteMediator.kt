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
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Remote mediator for accessing timelines that are not backed by the database. */
@OptIn(ExperimentalPagingApi::class)
class NetworkTimelineRemoteMediator(
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

        Log.d(TAG, "load(), LoadType = $loadType")

        return try {
            val key = when (loadType) {
                LoadType.REFRESH -> {
                    Log.d(TAG, "  anchorPosition: ${state.anchorPosition}")
                    // Find the closest page to the current position
                    val key = state.anchorPosition?.let { state.closestPageToPosition(it) }?.data?.last()?.id
                    Log.d(TAG, "  Refreshing page with last id: $key")

                    // Use its prevKey to refresh this page
                    key?.let { pageCache.lowerEntry(it)?.value?.prevKey }
                }
                LoadType.APPEND -> {
                    pageCache.firstEntry()?.value?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.PREPEND -> {
                    pageCache.lastEntry()?.value?.prevKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
            }

            Log.d(TAG, "  from key: $key")
            val response = fetchStatusPageByKind(loadType, key, state.config.initialLoadSize)

            val page = Page.tryFrom(response).getOrElse { return MediatorResult.Error(it) }

            if (BuildConfig.DEBUG && loadType == LoadType.REFRESH) {
                state.anchorPosition?.let { state.closestPageToPosition(it) }?.data?.last()?.id?.let { expectedPage ->
                    if (pageCache[expectedPage]?.data?.size == page.data.size) {
                        Log.d(TAG, "  Refresh has same data size")
                        if (pageCache[expectedPage]?.data?.last()?.id == page.data.last().id) {
                            Log.d(TAG, "  ... and they have the same last ID")
                        } else {
                            throw IllegalStateException("  Last IDs do not match, got: ${page.data.last().id}, want: $expectedPage")
                        }
                    } else {
                        Log.d(TAG, "  Refresh has different data size!")
                    }
                    page.data.lastOrNull { it.id == expectedPage }
                        ?: throw IllegalStateException("  Refreshed page does not contain $expectedPage")
                    Log.d(TAG, "  Page contains $expectedPage")
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
                Log.d(TAG, "  Invalidating source")
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
