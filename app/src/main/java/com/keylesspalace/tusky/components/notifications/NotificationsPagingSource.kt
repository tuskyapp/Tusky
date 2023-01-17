package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
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
                    // When given an ID the Mastodon API can either return the page of data
                    // immediately *after* that key, or the page of data immediately *before*
                    // that key.
                    //
                    // In both cases, the page of data *does not include* the item with the
                    // key you actually asked for.
                    //
                    // The result is that the item you asked for is one page higher, and is
                    // scrolled off the top of the screen.
                    //
                    // To work around this, fetch the single notification, and fake next/prev
                    // paging links.
                    params.key?.let { key ->
                        val response = mastodonApi.notification(id = key)
                        if (!response.isSuccessful) {
                            return@let Response.error(
                                response.code(),
                                response.errorBody() ?: "".toResponseBody()
                            )
                        }

                        val headers = Headers.Builder()
                            .addAll(response.headers())
                            .add(
                                "link: </?max_id=$key>; rel=\"next\", </?min_id=$key>; rel=\"prev\""
                            )
                            .build()
                        Response.success<List<Notification>>(listOf(response.body()!!), headers)
                    } ?: mastodonApi.notifications2(
                        limit = params.loadSize,
                        excludes = notificationFilter
                    )
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
