package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationsRepository @Inject constructor(
    private val mastodonApi: MastodonApi
) {
    private var factory: InvalidatingPagingSourceFactory<String, Notification>? = null

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

        factory = InvalidatingPagingSourceFactory {
            NotificationsPagingSource(mastodonApi, filter)
        }

        return Pager(
            config = PagingConfig(pageSize = pageSize),
            initialKey = initialKey,
            pagingSourceFactory = factory!!
        ).flow
    }

    /** Invalidate the active paging source, see [PagingSource.invalidate] */
    fun invalidate() {
        factory?.invalidate()
    }

    /** Clear notifications */
    fun clearNotifications(): Response<ResponseBody> {
        return mastodonApi.clearNotifications()
    }

    companion object {
        private const val TAG = "NotificationsRepository"
        private const val PAGE_SIZE = 30
    }
}
