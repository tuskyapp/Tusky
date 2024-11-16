package com.keylesspalace.tusky.components.notifications.requests

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.NotificationRequest

class NotificationRequestsPagingSource(
    private val requests: List<NotificationRequest>,
    private val nextKey: String?
) : PagingSource<String, NotificationRequest>() {
    override fun getRefreshKey(state: PagingState<String, NotificationRequest>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, NotificationRequest> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(requests.toList(), null, nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
