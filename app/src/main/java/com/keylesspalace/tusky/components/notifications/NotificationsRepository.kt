package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationsRepository @Inject constructor(
    private val mastodonApi: MastodonApi
) {

    /**
     * @return flow of Mastodon [Notification], excluding all types in [filter].
     * Notifications are loaded in [pageSize] increments.
     */
    fun getNotificationsStream(
        filter: Set<Notification.Type>,
        pageSize: Int = PAGE_SIZE,
        initialKey: String? = null
    ): Flow<PagingData<Notification>> {
        Log.d(TAG, "getNotificationsStream(), filtering: $filter")

        return Pager(
            config = PagingConfig(pageSize = pageSize),
            initialKey = initialKey,
            pagingSourceFactory = {
                NotificationsPagingSource(mastodonApi, filter)
            }
        ).flow
    }

    companion object {
        private const val TAG = "NotificationsRepository"
        private const val PAGE_SIZE = 30
    }
}
