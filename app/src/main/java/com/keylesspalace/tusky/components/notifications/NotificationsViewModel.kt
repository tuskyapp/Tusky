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
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FilterUpdatedEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder
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
import com.keylesspalace.tusky.usecase.NotificationPolicyState
import com.keylesspalace.tusky.usecase.NotificationPolicyUsecase
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    private val accountManager: AccountManager,
    private val preferences: SharedPreferences,
    private val filterModel: FilterModel,
    private val db: AppDatabase,
    private val notificationPolicyUsecase: NotificationPolicyUsecase
) : ViewModel() {

    val activeAccountFlow = accountManager.activeAccount(viewModelScope)

    private val accountId: Long = activeAccountFlow.value!!.id

    private val refreshTrigger = MutableStateFlow(0L)

    val excludes: StateFlow<Set<Notification.Type>> = activeAccountFlow
        .map { account ->
            account?.notificationsFilter.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, activeAccountFlow.value?.notificationsFilter.orEmpty())

    /** Map from notification id to translation. */
    private val translations = MutableStateFlow(mapOf<String, TranslationViewData>())

    private var remoteMediator = NotificationsRemoteMediator(this, accountManager, api, db)

    private var readingOrder: ReadingOrder =
        ReadingOrder.from(preferences.getString(PrefKeys.READING_ORDER, null))

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val notifications = refreshTrigger.flatMapLatest {
        Pager(
            config = PagingConfig(
                pageSize = LOAD_AT_ONCE
            ),
            remoteMediator = remoteMediator,
            pagingSourceFactory = {
                db.notificationsDao().getNotifications(accountId)
            }
        ).flow
            .cachedIn(viewModelScope)
            .combine(translations) { pagingData, translations ->
                pagingData.map { notification ->
                    val translation = translations[notification.status?.serverId]
                    notification.toViewData(translation = translation)
                }.filter { notificationViewData ->
                    shouldFilterStatus(notificationViewData) != Filter.Action.HIDE
                }
            }
    }
        .flowOn(Dispatchers.Default)

    val notificationPolicy: StateFlow<NotificationPolicyState> = notificationPolicyUsecase.state

    init {
        viewModelScope.launch {
            activeAccountFlow.collect {
                println("activeAccountFlow ${it?.notificationsFilter}")
            }
        }
        viewModelScope.launch {
            eventHub.events.collect { event ->
                if (event is PreferenceChangedEvent) {
                    onPreferenceChanged(event.preferenceKey)
                }
                if (event is FilterUpdatedEvent && event.filterContext.contains(Filter.Kind.NOTIFICATIONS.kind)) {
                    filterModel.init(Filter.Kind.NOTIFICATIONS)
                    refreshTrigger.value += 1
                }
            }
        }
        viewModelScope.launch {
            val needsRefresh = filterModel.init(Filter.Kind.NOTIFICATIONS)
            if (needsRefresh) {
                refreshTrigger.value++
            }
        }
        loadNotificationPolicy()
    }

    fun loadNotificationPolicy() {
        viewModelScope.launch {
            notificationPolicyUsecase.getNotificationPolicy()
        }
    }

    fun updateNotificationFilters(newFilters: Set<Notification.Type>) {
        val account = activeAccountFlow.value
        if (newFilters != excludes.value && account != null) {
            viewModelScope.launch {
                accountManager.updateAccount(account) {
                    copy(notificationsFilter = newFilters)
                }
                db.notificationsDao().cleanupNotifications(accountId, 0)
                refreshTrigger.value++
            }
        }
    }

    private fun shouldFilterStatus(notificationViewData: NotificationViewData): Filter.Action {
        return when ((notificationViewData as? NotificationViewData.Concrete)?.type) {
            Notification.Type.Mention, Notification.Type.Poll, Notification.Type.Status, Notification.Type.Update -> {
                val account = activeAccountFlow.value
                notificationViewData.statusViewData?.let { statusViewData ->
                    if (statusViewData.status.account.id == account?.accountId) {
                        return Filter.Action.NONE
                    }
                    statusViewData.filterAction = filterModel.shouldFilterStatus(statusViewData.actionable)
                    return statusViewData.filterAction
                }
                Filter.Action.NONE
            }

            else -> Filter.Action.NONE
        }
    }

    fun respondToFollowRequest(accept: Boolean, accountIdRequestingFollow: String, notificationId: String) {
        viewModelScope.launch {
            if (accept) {
                api.authorizeFollowRequest(accountIdRequestingFollow)
            } else {
                api.rejectFollowRequest(accountIdRequestingFollow)
            }.fold(
                onSuccess = {
                    // since the follow request has been responded, the notification can be deleted. The Ui will update automatically.
                    db.notificationsDao().delete(accountId, notificationId)
                    if (accept) {
                        // refresh the notifications so the new follow notification will be loaded
                        refreshTrigger.value++
                    }
                },
                onFailure = { t ->
                    Log.e(TAG, "Failed to to respond to follow request from account id $accountIdRequestingFollow.", t)
                }
            )
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
            db.timelineStatusDao()
                .setExpanded(accountId, status.id, expanded)
        }
    }

    fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao()
                .setContentShowing(accountId, status.id, isShowing)
        }
    }

    fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao()
                .setContentCollapsed(accountId, status.id, isCollapsed)
        }
    }

    fun remove(notificationId: String) {
        viewModelScope.launch {
            db.notificationsDao().delete(accountId, notificationId)
        }
    }

    fun clearWarning(status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao().clearWarning(accountId, status.actionableId)
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            api.clearNotifications().fold(
                {
                    db.notificationsDao().cleanupNotifications(accountId, 0)
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

                notificationsDao.insertNotification(
                    Placeholder(placeholderId, loading = true).toNotificationEntity(
                        accountId
                    )
                )

                val (idAbovePlaceholder, idBelowPlaceholder) = db.withTransaction {
                    notificationsDao.getIdAbove(accountId, placeholderId) to
                        notificationsDao.getIdBelow(accountId, placeholderId)
                }
                val response = when (readingOrder) {
                    // Using minId, loads up to LOAD_AT_ONCE statuses with IDs immediately
                    // after minId and no larger than maxId
                    ReadingOrder.OLDEST_FIRST -> api.notifications(
                        maxId = idAbovePlaceholder,
                        minId = idBelowPlaceholder,
                        limit = TimelineViewModel.LOAD_AT_ONCE,
                        excludes = excludes.value
                    )
                    // Using sinceId, loads up to LOAD_AT_ONCE statuses immediately before
                    // maxId, and no smaller than minId.
                    ReadingOrder.NEWEST_FIRST -> api.notifications(
                        maxId = idAbovePlaceholder,
                        sinceId = idBelowPlaceholder,
                        limit = TimelineViewModel.LOAD_AT_ONCE,
                        excludes = excludes.value
                    )
                }

                val notifications = response.body()
                if (!response.isSuccessful || notifications == null) {
                    loadMoreFailed(placeholderId, HttpException(response))
                    return@launch
                }

                val account = activeAccountFlow.value
                if (account == null) {
                    return@launch
                }

                val statusDao = db.timelineStatusDao()
                val accountDao = db.timelineAccountDao()

                db.withTransaction {
                    notificationsDao.delete(accountId, placeholderId)

                    val overlappedNotifications = if (notifications.isNotEmpty()) {
                        notificationsDao.deleteRange(
                            accountId,
                            notifications.last().id,
                            notifications.first().id
                        )
                    } else {
                        0
                    }

                    for (notification in notifications) {
                        accountDao.insert(notification.account.toEntity(accountId))
                        notification.report?.let { report ->
                            accountDao.insert(report.targetAccount.toEntity(accountId))
                            notificationsDao.insertReport(report.toEntity(accountId))
                        }
                        notification.status?.let { status ->
                            val statusToInsert = status.reblog ?: status
                            accountDao.insert(statusToInsert.account.toEntity(accountId))

                            statusDao.insert(
                                statusToInsert.toEntity(
                                    tuskyAccountId = accountId,
                                    expanded = account.alwaysOpenSpoiler,
                                    contentShowing = account.alwaysShowSensitiveMedia || !status.sensitive,
                                    contentCollapsed = true
                                )
                            )
                        }
                        notificationsDao.insertNotification(
                            notification.toEntity(
                                accountId
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
                            ReadingOrder.OLDEST_FIRST -> notifications.first().id
                            ReadingOrder.NEWEST_FIRST -> notifications.last().id
                        }
                        notificationsDao.insertNotification(
                            Placeholder(
                                idToConvert,
                                loading = false
                            ).toNotificationEntity(accountId)
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

    private fun onPreferenceChanged(key: String) {
        when (key) {
            PrefKeys.READING_ORDER -> {
                readingOrder = ReadingOrder.from(
                    preferences.getString(PrefKeys.READING_ORDER, null)
                )
            }
        }
    }

    companion object {
        private const val LOAD_AT_ONCE = 30
        private const val TAG = "NotificationsViewModel"
    }
}
