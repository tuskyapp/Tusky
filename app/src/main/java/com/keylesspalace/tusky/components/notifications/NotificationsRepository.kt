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
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.google.gson.Gson
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject

class NotificationsRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val gson: Gson
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
            NotificationsPagingSource(mastodonApi, gson, filter)
        }

        return Pager(
            config = PagingConfig(pageSize = pageSize, initialLoadSize = pageSize),
            initialKey = initialKey,
            pagingSourceFactory = factory!!
        ).flow
    }

    /** Invalidate the active paging source, see [PagingSource.invalidate] */
    fun invalidate() {
        factory?.invalidate()
    }

    /** Clear notifications */
    suspend fun clearNotifications(): Response<ResponseBody> {
        return mastodonApi.clearNotifications()
    }

    companion object {
        private const val TAG = "NotificationsRepository"
        private const val PAGE_SIZE = 30
    }
}
