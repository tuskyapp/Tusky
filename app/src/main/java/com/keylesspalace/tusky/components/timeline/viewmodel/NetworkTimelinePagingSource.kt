/* Copyright 2021 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.timeline.viewmodel

import android.os.Parcelable
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.parcelize.Parcelize
import okhttp3.Headers
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

// TODO(https://github.com/tuskyapp/Tusky/issues/3432)
// This is extremely similar to NotificationsPagingSource. Merging the code, or making it generic
// over the type of data returned (Notification, Status, etc) is probably warranted.

/** Models next/prev links from the "Links" header in an API response */
data class Links(val next: String?, val prev: String?)

/** A timeline's type. Hold's data necessary to display that timeline. */
@Parcelize
sealed class TimelineKind : Parcelable {
    object Home : TimelineKind()
    object PublicFederated : TimelineKind()
    object PublicLocal : TimelineKind()
    data class Tag(val tags: List<String>) : TimelineKind()
    /** Any timeline showing statuses from a single user */
    @Parcelize
    sealed class User(open val id: String) : TimelineKind() {
        /** Timeline showing just the user's statuses (no replies) */
        data class Posts(override val id: String) : User(id)
        /** Timeline showing the user's pinned statuses */
        data class Pinned(override val id: String) : User(id)
        /** Timeline showing the user's top-level statuses and replies they have made */
        data class Replies(override val id: String) : User(id)
    }
    object Favourites : TimelineKind()
    object Bookmarks : TimelineKind()
    data class UserList(val id: String, val title: String) : TimelineKind()
}

/** [PagingSource] for Mastodon Status, identified by the Status ID */
class NetworkTimelinePagingSource @Inject constructor(
    private val api: MastodonApi,
    private val kind: TimelineKind
) : PagingSource<String, Status>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Status> {
        Log.d(TAG, "load() with ${params.javaClass.simpleName} for key: ${params.key}")

        try {
            val response = when (params) {
                is LoadParams.Refresh -> {
                    getInitialPage(params)
                }
                is LoadParams.Append -> fetchStatusesForKind(
                    maxId = params.key,
                    limit = params.loadSize,
                )
                is LoadParams.Prepend -> fetchStatusesForKind(
                    minId = params.key,
                    limit = params.loadSize,
                )
            }

            if (!response.isSuccessful) {
                return LoadResult.Error(Throwable(response.errorBody()?.string()))
            }

            val links = getPageLinks(response.headers()["link"])
            return LoadResult.Page(
                data = response.body()!!,
                nextKey = links.next,
                prevKey = links.prev
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }

    @Throws(IOException::class, HttpException::class)
    suspend fun fetchStatusesForKind(
        maxId: String? = null,
        minId: String? = null,
        limit: Int
    ): Response<List<Status>> {
        // TODO: These probably shouldn't be `sinceId` but `minId` in the API calls
        return when (kind) {
            is TimelineKind.Home -> api.homeTimeline(maxId = maxId, sinceId = minId, limit = limit)
            is TimelineKind.PublicFederated -> api.publicTimeline(null, maxId, minId, limit)
            is TimelineKind.PublicLocal -> api.publicTimeline(true, maxId, minId, limit)
            is TimelineKind.Tag -> {
                val firstHashtag = kind.tags.first()
                val additionalHashtags = kind.tags.subList(1, kind.tags.size)
                api.hashtagTimeline(firstHashtag, additionalHashtags, null, maxId, minId, limit)
            }
            is TimelineKind.User.Posts -> api.accountStatuses(
                kind.id,
                maxId,
                minId,
                limit,
                excludeReplies = true,
                onlyMedia = null,
                pinned = null
            )
            is TimelineKind.User.Pinned -> api.accountStatuses(
                kind.id,
                maxId,
                minId,
                limit,
                excludeReplies = null,
                onlyMedia = null,
                pinned = true
            )
            is TimelineKind.User.Replies -> api.accountStatuses(
                kind.id,
                maxId,
                minId,
                limit,
                excludeReplies = null,
                onlyMedia = null,
                pinned = null
            )
            is TimelineKind.Favourites -> api.favourites(maxId, minId, limit)
            is TimelineKind.Bookmarks -> api.bookmarks(maxId, minId, limit)
            is TimelineKind.UserList -> api.listTimeline(kind.id, maxId, minId, limit)
        }
    }

    /**
     * Fetch the initial page, using params.key as the ID of the initial item to fetch.
     *
     * - If there is no key the most recent page is returned
     * - If the notification exists, and is not filtered, a page of notifications is returned
     * - If the notification does not exist, or is filtered, the page of notifications immediately
     *   before is returned
     * - If there is no page of notifications immediately before then the page immediately after
     *   is returned
     */
    // TODO: This is not directly usable from NotificationsPagingSource, as NotificationsPagingSource
    // has to handle filtering results as well.
    //
    // In addition, the notification and status API calls return different types (statuses return
    // NetworkResult, notifications returns Response
    private suspend fun getInitialPage(params: LoadParams<String>): Response<List<Status>> = coroutineScope {
        // If the key is null this is straightforward, just return the most recent page
        val key = params.key ?: return@coroutineScope fetchStatusesForKind(limit = params.loadSize)

        // It's important to return *something* from this state. If an empty page is returned
        // (even with next/prev links) Pager3 assumes there is no more data to load and stops.
        //
        // In addition, the Mastodon API does not let you fetch a page that contains a given key.
        // You can fetch the page immediately before the key, or the page immediately after, but
        // you can not fetch the page itself.

        // First, try and get the status itself, and the page of statuses immediately before
        // it. This is so that a full page of results can be returned. Returning just the
        // single status means the displayed list can jump around a bit as more data is
        // loaded.
        //
        // Make both requests, and wait for the first to complete.
        val deferredStatus = async { api.status(statusId = key) }
        val deferredStatusPage = async {
            fetchStatusesForKind(maxId = key, limit = params.loadSize)
        }

        deferredStatus.await().getOrNull()?.let {
            val statuses = mutableListOf(it)

            // The status() call returns a NetworkResult, the others return a Response (!)
            // so convert between them.
            deferredStatusPage.await().body()?.let {
                statuses.addAll(it)
            }

            // "statuses" now contains at least one status we can return, and
            // hopefully a full page.

            // Build correct max_id and min_id links for the response. The "min_id" to use
            // when fetching the next page is the same as "key". The "max_id" is the ID of
            // the oldest status in the list.
            val maxId = statuses.last().id
            val headers = Headers.Builder()
                .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$key>; rel=\"prev\"")
                .build()

            return@coroutineScope Response.success(statuses, headers)
        }

        // The user's last read status was missing or is filtered. Use the page of
        // statuses chronologically older than their desired status.
        deferredStatusPage.await().apply {
            if (this.isSuccessful) return@coroutineScope this
        }

        // There were no statuses older than the user's desired status. Return the page
        // of statuses immediately newer than their desired status.
        return@coroutineScope fetchStatusesForKind(minId = key, limit = params.loadSize)
    }

    private fun getPageLinks(linkHeader: String?): Links {
        val links = HttpHeaderLink.parse(linkHeader)
        return Links(
            next = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter(
                "max_id"
            ),
            prev = HttpHeaderLink.findByRelationType(links, "prev")?.uri?.getQueryParameter(
                "min_id"
            )
        )
    }

    override fun getRefreshKey(state: PagingState<String, Status>): String? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }

    companion object {
        private const val TAG = "NetworkTimelinePagingSource"
    }
}
