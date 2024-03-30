/* Copyright 2024 Tusky Contributors
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
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.EmptyPagingSource
import com.keylesspalace.tusky.util.deserialize
import com.keylesspalace.tusky.util.serialize
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotificationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    private val accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    private val filterModel: FilterModel,
    private val db: AppDatabase,
    private val gson: Gson
) : ViewModel() {

    private val _filters = MutableStateFlow(
        accountManager.activeAccount?.let { account -> deserialize(account.notificationsFilter) } ?: emptySet()
    )
    val filters: StateFlow<Set<Notification.Type>> = _filters.asStateFlow()

    private var remoteMediator = NotificationsRemoteMediator(accountManager, api, db, gson, filters.value)

    @OptIn(ExperimentalPagingApi::class)
    val notifications = Pager(
        config = PagingConfig(pageSize = LOAD_AT_ONCE),
        remoteMediator = remoteMediator,
        pagingSourceFactory = {
            val activeAccount = accountManager.activeAccount
            if (activeAccount == null) {
                EmptyPagingSource()
            } else {
                db.notificationsDao().getNotifications(activeAccount.id)
            }
        }
    ).flow
        .map { pagingData ->
            pagingData.map(Dispatchers.Default.asExecutor()) { notification ->
                notification.toViewData(gson)
            }.filter(Dispatchers.Default.asExecutor()) { notificationViewData ->
                shouldFilterStatus(notificationViewData) != Filter.Action.HIDE
            }
        }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)

    fun updateNotificationFilters(newFilters: Set<Notification.Type>) {
        if (newFilters != _filters.value) {
            val account = accountManager.activeAccount
            if (account != null) {
                viewModelScope.launch {
                    account.notificationsFilter = serialize(newFilters)
                    accountManager.saveAccount(account)
                    remoteMediator.excludes = newFilters
                    // clear the cache
                    db.notificationsDao().cleanupNotifications(account.id, 0)
                    _filters.value = newFilters
                }
            }
        }
    }

    private fun shouldFilterStatus(notificationViewData: NotificationViewData): Filter.Action {
        return when ((notificationViewData as? NotificationViewData.Concrete)?.type) {
            Notification.Type.MENTION, Notification.Type.STATUS, Notification.Type.POLL -> {
                notificationViewData.statusViewData?.let { statusViewData ->
                    statusViewData.filterAction = filterModel.shouldFilterStatus(statusViewData.actionable)
                    return statusViewData.filterAction
                }
                Filter.Action.NONE
            }
            else -> Filter.Action.NONE
        }
    }

    fun respondToFollowRequest(accept: Boolean, accountId: String, notificationId: String): Flow<Boolean> {
        return callbackFlow {
            viewModelScope.launch {
                if (accept) {
                    api.authorizeFollowRequest(accountId)
                } else {
                    api.rejectFollowRequest(accountId)
                }.fold(
                    onSuccess = {
                        // since the follow request has been responded, the notification can be deleted. The Ui will update automatically.
                        db.notificationsDao().deleteRange(accountManager.activeAccount!!.id, notificationId, notificationId)
                        if (accept) {
                            // Accepting a follow request will generate a new follow notification.
                            // For it to show up, notifications need to be refreshed which is done easiest by refreshing the adapter in the Fragment.
                            // We use this boolean to signal the need for refreshing to the ui.
                            send(true)
                        }
                    },
                    onFailure = { t ->
                        Log.e(TAG, "Failed to to respond to follow request from account id $accountId.", t)
                    }
                )
            }
            awaitClose()
        }
    }

    fun reblog(reblog: Boolean, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        timelineCases.reblog(status.actionableId, reblog).onFailure { t ->
            ifExpected(t) {
                Log.w(TAG, "Failed to reblog status " + status.actionableId, t)
            }
        }
    }

    fun favorite(favorite: Boolean, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        timelineCases.favourite(status.actionableId, favorite).onFailure { t ->
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun bookmark(bookmark: Boolean, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        timelineCases.bookmark(status.actionableId, bookmark).onFailure { t ->
            ifExpected(t) {
                Log.d(TAG, "Failed to bookmark status " + status.actionableId, t)
            }
        }
    }

    fun voteInPoll(choices: List<Int>, status: StatusViewData.Concrete) = viewModelScope.launch {
        val poll = status.status.actionableStatus.poll ?: run {
            Log.d(TAG, "No poll on status ${status.id}")
            return@launch
        }
        timelineCases.voteInPoll(status.actionableId, poll.id, choices).onFailure { t ->
            ifExpected(t) {
                Log.d(TAG, "Failed to vote in poll: " + status.actionableId, t)
            }
        }
    }

    fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao().setExpanded(accountManager.activeAccount!!.id, status.id, expanded)
        }
    }

    fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao()
                .setContentShowing(accountManager.activeAccount!!.id, status.id, isShowing)
        }
    }

    fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao()
                .setContentCollapsed(accountManager.activeAccount!!.id, status.id, isCollapsed)
        }
    }

    fun remove(notificationId: String) {
        viewModelScope.launch {
            db.notificationsDao().deleteRange(accountManager.activeAccount!!.id, notificationId, notificationId)
        }
    }

    fun removeAllByAccountId(accountId: String) {
        viewModelScope.launch {
            db.timelineDao().removeAllByUser(accountManager.activeAccount!!.id, accountId)
        }
    }

    fun removeAllByInstance(instance: String) {
        viewModelScope.launch {
            db.timelineDao().deleteAllFromInstance(accountManager.activeAccount!!.id, instance)
        }
    }

    fun clearWarning(status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao().clearWarning(accountManager.activeAccount!!.id, status.actionableId)
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            api.clearNotifications().fold(
                {
                    db.notificationsDao().cleanupNotifications(accountManager.activeAccount!!.id, 0)
                },
                { t ->
                    Log.w(TAG, "failed to clear notifications", t)
                }
            )
        }
    }

    companion object {
        private const val LOAD_AT_ONCE = 30
        private const val TAG = "NotificationsViewModel"
    }
}
