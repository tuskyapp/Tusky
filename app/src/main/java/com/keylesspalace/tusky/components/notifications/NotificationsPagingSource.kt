package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import okhttp3.Headers
import retrofit2.Response
import javax.inject.Inject

/** Models next/prev links from the "Links" header in an API response */
data class Links(val next: String?, val prev: String?)

class NotificationsPagingSource @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val notificationFilter: Set<Notification.Type>
) : PagingSource<String, Notification>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Notification> {
        Log.d(TAG, "load() with ${params.javaClass.simpleName} for key: ${params.key}")

        try {
            val response = when (params) {
                is LoadParams.Refresh -> {
                    getInitialPage(params)
                }
                is LoadParams.Append -> mastodonApi.notifications2(
                    maxId = params.key,
                    limit = params.loadSize,
                    excludes = notificationFilter
                )
                is LoadParams.Prepend -> mastodonApi.notifications2(
                    minId = params.key,
                    limit = params.loadSize,
                    excludes = notificationFilter
                )
            }

            if (!response.isSuccessful) {
                return LoadResult.Error(Throwable(response.errorBody().toString()))
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

    private suspend fun getInitialPage(params: LoadParams<String>): Response<List<Notification>> {
        // If the key is null this is straightforward, just return the most recent notifications.
        val key = params.key
            ?: return mastodonApi.notifications2(
                limit = params.loadSize,
                excludes = notificationFilter
            )

        // It's important to return *something* from this state. If an empty page is returned
        // (even with next/prev links) Pager3 assumes there is no more data to load and stops.
        //
        // In addition, the Mastodon API does not let you fetch a page that contains a given key.
        // You can fetch the page immediately before the key, or the page immediately after, but
        // you can not fetch the page itself.
        //
        // To solve this, perform potentially multiple fetches.

        // First, try and get the notification itself.
        val keyNotificationResponse = mastodonApi.notification(id = key)

        // If this was successful we must still check that the user is not filtering this type
        // of notification, as fetching a single notification ignores filters. Returning this
        // notification if the user is filtering the type is wrong.
        if (keyNotificationResponse.isSuccessful) {
            if (!notificationFilter.contains(keyNotificationResponse.body()!!.type)) {
                // Notification is *not* filtered. We can return this, but need to add fake
                // "next" and "prev" links so that paging works (the Mastodon response does not
                // include the "link" header for a single notification).
                val headers = Headers.Builder()
                    .addAll(keyNotificationResponse.headers())
                    .add(
                        "link: </?max_id=$key>; rel=\"next\", </?min_id=$key>; rel=\"prev\""
                    )
                    .build()

                return Response.success(listOf(keyNotificationResponse.body()!!), headers)
            }
        }

        // The user's last read notification was missing or is filtered. Try and fetch the page
        // of notifications chronologically older than their desired notification
        mastodonApi.notifications2(
            maxId = key,
            limit = params.loadSize,
            excludes = notificationFilter
        ).apply {
            if (this.isSuccessful) return this
        }

        // If the list is still empty then the there were no notifications older than the user's
        // desired notification. Return the page of notifications immediately newer than their
        // desired notification.
        return mastodonApi.notifications2(
            minId = key,
            limit = params.loadSize,
            excludes = notificationFilter
        )
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
