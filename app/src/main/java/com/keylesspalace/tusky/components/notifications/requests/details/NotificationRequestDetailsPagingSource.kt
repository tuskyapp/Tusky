package com.keylesspalace.tusky.components.notifications.requests.details

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.viewdata.NotificationViewData

class NotificationRequestDetailsPagingSource(
    private val notifications: List<NotificationViewData>,
    private val nextKey: String?
) : PagingSource<String, NotificationViewData>() {
    override fun getRefreshKey(state: PagingState<String, NotificationViewData>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, NotificationViewData> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(notifications.toList(), null, nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
