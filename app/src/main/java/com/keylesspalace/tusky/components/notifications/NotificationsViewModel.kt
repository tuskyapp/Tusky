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
import androidx.room.withTransaction
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.map
import at.connyduck.calladapter.networkresult.onFailure
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.preference.PreferencesFragment
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.EmptyPagingSource
import com.keylesspalace.tusky.util.deserialize
import com.keylesspalace.tusky.util.serialize
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import retrofit2.HttpException

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

    /** Map from status id to translation. */
    private val translations = MutableStateFlow(mapOf<String, TranslationViewData>())

    private var remoteMediator = NotificationsRemoteMediator(accountManager, api, db, gson, filters.value)

    private var readingOrder: PreferencesFragment.ReadingOrder =
        PreferencesFragment.ReadingOrder.from(sharedPreferences.getString(PrefKeys.READING_ORDER, null))

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
        .cachedIn(viewModelScope)
        .combine(translations) { pagingData, translations ->
            pagingData.map(Dispatchers.Default.asExecutor()) { notification ->
                val translation = translations[notification.status?.serverId]
                notification.toViewData(
                    gson,
                    translation = translation
                )
            }.filter(Dispatchers.Default.asExecutor()) { notificationViewData ->
                shouldFilterStatus(notificationViewData) != Filter.Action.HIDE
            }
        }
        .flowOn(Dispatchers.Default)

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
                        db.notificationsDao().delete(accountManager.activeAccount!!.id, notificationId)
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
            db.notificationsDao().delete(accountManager.activeAccount!!.id, notificationId)
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

    suspend fun translate(status: StatusViewData.Concrete): NetworkResult<Unit> {
        translations.value += (status.id to TranslationViewData.Loading)
        return timelineCases.translate(status.actionableId)
            .map { translation ->
                translations.value += (status.id to TranslationViewData.Loaded(translation))
            }
            .onFailure {
                translations.value -= status.id
            }
    }

    fun untranslate(status: StatusViewData.Concrete) {
        translations.value -= status.id
    }

    fun loadMore(placeholderId: String) {
        viewModelScope.launch {
            try {
                val notificationsDao = db.notificationsDao()

                val activeAccount = accountManager.activeAccount!!

                notificationsDao.insertNotification(
                    Placeholder(placeholderId, loading = true).toNotificationEntity(
                        activeAccount.id
                    )
                )

                val response = db.withTransaction {
                    val idAbovePlaceholder = notificationsDao.getIdAbove(activeAccount.id, placeholderId)
                    val idBelowPlaceholder = notificationsDao.getIdBelow(activeAccount.id, placeholderId)
                    when (readingOrder) {
                        // Using minId, loads up to LOAD_AT_ONCE statuses with IDs immediately
                        // after minId and no larger than maxId
                        PreferencesFragment.ReadingOrder.OLDEST_FIRST -> api.notifications(
                            maxId = idAbovePlaceholder,
                            minId = idBelowPlaceholder,
                            limit = TimelineViewModel.LOAD_AT_ONCE
                        )
                        // Using sinceId, loads up to LOAD_AT_ONCE statuses immediately before
                        // maxId, and no smaller than minId.
                        PreferencesFragment.ReadingOrder.NEWEST_FIRST -> api.notifications(
                            maxId = idAbovePlaceholder,
                            sinceId = idBelowPlaceholder,
                            limit = TimelineViewModel.LOAD_AT_ONCE
                        )
                    }
                }

                val notifications = response.body()
                if (!response.isSuccessful || notifications == null) {
                    loadMoreFailed(placeholderId, HttpException(response))
                    return@launch
                }

                val timelineDao = db.timelineDao()

                db.withTransaction {
                    notificationsDao.delete(activeAccount.id, placeholderId)

                    val overlappedNotifications = if (notifications.isNotEmpty()) {
                        notificationsDao.deleteRange(
                            activeAccount.id,
                            notifications.last().id,
                            notifications.first().id
                        )
                    } else {
                        0
                    }

                    for (notification in notifications) {
                        timelineDao.insertAccount(notification.account.toEntity(activeAccount.id, gson))
                        notification.report?.let { report ->
                            timelineDao.insertAccount(report.targetAccount.toEntity(activeAccount.id, gson))
                            notificationsDao.insertReport(report.toEntity(activeAccount.id))
                        }
                        notification.status?.let { status ->
                            timelineDao.insertAccount(status.account.toEntity(activeAccount.id, gson))

                            timelineDao.insertStatus(
                                status.toEntity(
                                    tuskyAccountId = activeAccount.id,
                                    gson = gson,
                                    expanded = activeAccount.alwaysOpenSpoiler,
                                    contentShowing = activeAccount.alwaysShowSensitiveMedia || !status.sensitive,
                                    contentCollapsed = true
                                )
                            )
                        }
                        notificationsDao.insertNotification(
                            notification.toEntity(
                                activeAccount.id
                            )
                        )
                    }

                    /* In case we loaded a whole page and there was no overlap with existing notifications,
                       we insert a placeholder because there might be even more unknown notifications */
                    if (overlappedNotifications == 0 && notifications.size == TimelineViewModel.LOAD_AT_ONCE) {
                        /* This overrides the first/last of the newly loaded notifications with a placeholder
                           to guarantee the placeholder has an id that exists on the server as not all
                           servers handle client generated ids as expected */
                        val idToConvert = when (readingOrder) {
                            PreferencesFragment.ReadingOrder.OLDEST_FIRST -> notifications.first().id
                            PreferencesFragment.ReadingOrder.NEWEST_FIRST -> notifications.last().id
                        }
                        notificationsDao.insertNotification(
                            Placeholder(
                                idToConvert,
                                loading = false
                            ).toNotificationEntity(activeAccount.id)
                        )
                    }
                }
            } catch (e: Exception) {
                ifExpected(e) {
                    loadMoreFailed(placeholderId, e)
                }
            }
        }
    }

    private suspend fun loadMoreFailed(placeholderId: String, e: Exception) {
        Log.w(TAG, "failed loading notifications", e)
        val activeAccount = accountManager.activeAccount!!
        db.notificationsDao()
            .insertNotification(
                Placeholder(placeholderId, loading = false).toNotificationEntity(activeAccount.id)
            )
    }

    companion object {
        private const val LOAD_AT_ONCE = 30
        private const val TAG = "NotificationsViewModel"
    }
}
