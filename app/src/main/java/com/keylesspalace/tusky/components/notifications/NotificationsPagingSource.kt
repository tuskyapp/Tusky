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

package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.gson.Gson
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import retrofit2.Response
import javax.inject.Inject

/** Models next/prev links from the "Links" header in an API response */
data class Links(val next: String?, val prev: String?) {
    companion object {
        fun from(linkHeader: String?): Links {
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
    }
}

/** [PagingSource] for Mastodon Notifications, identified by the Notification ID */
class NotificationsPagingSource @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val gson: Gson,
    private val notificationFilter: Set<Notification.Type>
) : PagingSource<String, Notification>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Notification> {
        Log.d(TAG, "load() with ${params.javaClass.simpleName} for key: ${params.key}")

        try {
            val response = when (params) {
                is LoadParams.Refresh -> {
                    getInitialPage(params)
                }
                is LoadParams.Append -> mastodonApi.notifications(
                    maxId = params.key,
                    limit = params.loadSize,
                    excludes = notificationFilter
                )
                is LoadParams.Prepend -> mastodonApi.notifications(
                    minId = params.key,
                    limit = params.loadSize,
                    excludes = notificationFilter
                )
            }

            if (!response.isSuccessful) {
                val code = response.code()

                val msg = response.errorBody()?.string()?.let { errorBody ->
                    if (errorBody.isBlank()) return@let "no reason given"

                    val error = try {
                        gson.fromJson(errorBody, com.keylesspalace.tusky.entity.Error::class.java)
                    } catch (e: Exception) {
                        return@let "$errorBody ($e)"
                    }

                    when (val desc = error.error_description) {
                        null -> error.error
                        else -> "${error.error}: $desc"
                    }
                } ?: "no reason given"
                return LoadResult.Error(Throwable("HTTP $code: $msg"))
            }

            val links = Links.from(response.headers()["link"])
            return LoadResult.Page(
                data = response.body()!!,
                nextKey = links.next,
                prevKey = links.prev
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }

    /**
     * Fetch the initial page of notifications, using params.key as the ID of the initial
     * notification to fetch.
     *
     * - If there is no key, a page of the most recent notifications is returned
     * - If the notification exists, and is not filtered, a page of notifications is returned
     * - If the notification does not exist, or is filtered, the page of notifications immediately
     *   before is returned (if non-empty)
     * - If there is no page of notifications immediately before then the page immediately after
     *   is returned (if non-empty)
     * - Finally, fall back to the most recent notifications
     */
    private suspend fun getInitialPage(params: LoadParams<String>): Response<List<Notification>> = coroutineScope {
        // If the key is null this is straightforward, just return the most recent notifications.
        val key = params.key
            ?: return@coroutineScope mastodonApi.notifications(
                limit = params.loadSize,
                excludes = notificationFilter
            )

        // It's important to return *something* from this state. If an empty page is returned
        // (even with next/prev links) Pager3 assumes there is no more data to load and stops.
        //
        // In addition, the Mastodon API does not let you fetch a page that contains a given key.
        // You can fetch the page immediately before the key, or the page immediately after, but
        // you can not fetch the page itself.

        // First, try and get the notification itself, and the notifications immediately before
        // it. This is so that a full page of results can be returned. Returning just the
        // single notification means the displayed list can jump around a bit as more data is
        // loaded.
        //
        // Make both requests, and wait for the first to complete.
        val deferredNotification = async { mastodonApi.notification(id = key) }
        val deferredNotificationPage = async {
            mastodonApi.notifications(maxId = key, limit = params.loadSize, excludes = notificationFilter)
        }

        val notification = deferredNotification.await()
        if (notification.isSuccessful) {
            // If this was successful we must still check that the user is not filtering this type
            // of notification, as fetching a single notification ignores filters. Returning this
            // notification if the user is filtering the type is wrong.
            notification.body()?.let { body ->
                if (!notificationFilter.contains(body.type)) {
                    // Notification is *not* filtered. We can return this, but need the next page of
                    // notifications as well

                    // Collect all notifications in to this list
                    val notifications = mutableListOf(body)
                    val notificationPage = deferredNotificationPage.await()
                    if (notificationPage.isSuccessful) {
                        notificationPage.body()?.let {
                            notifications.addAll(it)
                        }
                    }

                    // "notifications" now contains at least one notification we can return, and
                    // hopefully a full page.

                    // Build correct max_id and min_id links for the response. The "min_id" to use
                    // when fetching the next page is the same as "key". The "max_id" is the ID of
                    // the oldest notification in the list.
                    val maxId = notifications.last().id
                    val headers = Headers.Builder()
                        .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$key>; rel=\"prev\"")
                        .build()

                    return@coroutineScope Response.success(notifications, headers)
                }
            }
        }

        // The user's last read notification was missing or is filtered. Use the page of
        // notifications chronologically older than their desired notification. This page must
        // *not* be empty (as noted earlier, if it is, paging stops).
        deferredNotificationPage.await().let { response ->
            if (response.isSuccessful) {
                if (!response.body().isNullOrEmpty()) return@coroutineScope response
            }
        }

        // There were no notifications older than the user's desired notification. Return the page
        // of notifications immediately newer than their desired notification. This page must
        // *not* be empty (as noted earlier, if it is, paging stops).
        mastodonApi.notifications(minId = key, limit = params.loadSize, excludes = notificationFilter).let { response ->
            if (response.isSuccessful) {
                if (!response.body().isNullOrEmpty()) return@coroutineScope response
            }
        }

        // Everything failed -- fallback to fetching the most recent notifications
        return@coroutineScope mastodonApi.notifications(
            limit = params.loadSize,
            excludes = notificationFilter
        )
    }

    override fun getRefreshKey(state: PagingState<String, Notification>): String? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }

    companion object {
        private const val TAG = "NotificationsPagingSource"
    }
}
