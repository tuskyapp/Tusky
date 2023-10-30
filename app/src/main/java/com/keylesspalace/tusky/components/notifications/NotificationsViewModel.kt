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

package com.keylesspalace.tusky.components.notifications

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import androidx.room.withTransaction
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder.NEWEST_FIRST
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder.OLDEST_FIRST
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.components.timeline.toViewData
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.NotificationDataEntity
import com.keylesspalace.tusky.db.NotificationEntity
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.EmptyPagingSource
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * TimelineViewModel that caches all statuses in a local database
 */
class NotificationsViewModel @Inject constructor(
    timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel,
    private val db: AppDatabase,
    private val gson: Gson
) : ViewModel() {

    private var currentPagingSource: PagingSource<Int, NotificationDataEntity>? = null

    @OptIn(ExperimentalPagingApi::class)
    val notifications = Pager(
        config = PagingConfig(pageSize = LOAD_AT_ONCE),
        remoteMediator = NotificationsRemoteMediator(accountManager, api, db, gson),
        pagingSourceFactory = {
            val activeAccount = accountManager.activeAccount
            if (activeAccount == null) {
                EmptyPagingSource()
            } else {
                db.notificationsDao().getNotifications(activeAccount.id)
            }.also { newPagingSource ->
                this.currentPagingSource = newPagingSource
            }
        }
    ).flow
        .map { pagingData ->
            pagingData.map(Dispatchers.Default.asExecutor()) { notification ->
                notification.toViewData(gson)
            }/*.filter(Dispatchers.Default.asExecutor()) { statusViewData ->
                shouldFilterStatus(statusViewData) != Filter.Action.HIDE
            }*/
        }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)


    companion object {
        private const val LOAD_AT_ONCE = 30
        private const val TAG = "NotificationsViewModel"
    }
}
