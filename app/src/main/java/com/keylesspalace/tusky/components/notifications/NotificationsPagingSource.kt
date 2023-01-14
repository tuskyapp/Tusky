package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import javax.inject.Inject

/** Models next/prev links from the "Links" header in an API response */
data class Links(val next: String?, val prev: String?)

class NotificationsPagingSource @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val notificationFilter: Set<Notification.Type>
) : PagingSource<String, Notification>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Notification> {
        Log.d(TAG, "load() with ${params.javaClass.simpleName} for key: ${params.key}")

        val response = when (params) {
            is LoadParams.Refresh -> mastodonApi.notifications2(
                limit = params.loadSize,
                excludes = notificationFilter
            )
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
            // TODO: Handle reporting failures correctly
            return LoadResult.Error(Throwable(response.errorBody().toString()))
        }

        // TODO: Report loading status correctly
        val links = getPageLinks(response.headers()["link"])
        return LoadResult.Page(
            data = response.body()!!,
            nextKey = links.next,
            prevKey = links.prev
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
