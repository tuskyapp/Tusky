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

package com.keylesspalace.tusky.components.notifications.requests.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.map
import at.connyduck.calladapter.networkresult.onFailure
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = NotificationRequestDetailsViewModel.Factory::class)
class NotificationRequestDetailsViewModel @AssistedInject constructor(
    val api: MastodonApi,
    val accountManager: AccountManager,
    val timelineCases: TimelineCases,
    val eventHub: EventHub,
    @Assisted("notificationRequestId") val notificationRequestId: String,
    @Assisted("accountId") val accountId: String
) : ViewModel() {

    var currentSource: NotificationRequestDetailsPagingSource? = null

    val notificationData: MutableList<NotificationViewData.Concrete> = mutableListOf()

    var nextKey: String? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20
        ),
        remoteMediator = NotificationRequestDetailsRemoteMediator(this),
        pagingSourceFactory = {
            NotificationRequestDetailsPagingSource(
                notifications = notificationData,
                nextKey = nextKey
            ).also { source ->
                currentSource = source
            }
        }
    ).flow
        .cachedIn(viewModelScope)

    private val _error = MutableSharedFlow<Throwable>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val error: SharedFlow<Throwable> = _error.asSharedFlow()

    private val _finish = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val finish: SharedFlow<Unit> = _finish.asSharedFlow()

    init {
        viewModelScope.launch {
            eventHub.events
                .collect { event ->
                    when (event) {
                        is StatusChangedEvent -> updateStatus(event.status)
                        is BlockEvent -> removeIfAccount(event.accountId)
                        is MuteEvent -> removeIfAccount(event.accountId)
                    }
                }
        }
    }

    fun acceptNotificationRequest() {
        viewModelScope.launch {
            api.acceptNotificationRequest(notificationRequestId).fold(
                {
                    _finish.emit(Unit)
                },
                { error ->
                    Log.w(TAG, "failed to dismiss notifications request", error)
                    _error.emit(error)
                }
            )
        }
    }

    fun dismissNotificationRequest() {
        viewModelScope.launch {
            api.dismissNotificationRequest(notificationRequestId).fold({
                _finish.emit(Unit)
            }, { error ->
                Log.w(TAG, "failed to dismiss notifications request", error)
                _error.emit(error)
            })
        }
    }

    private fun updateStatus(status: Status) {
        val position = notificationData.indexOfFirst { it.asStatusOrNull()?.id == status.id }
        if (position == -1) {
            return
        }
        val viewData = notificationData[position].statusViewData?.copy(status = status)
        notificationData[position] = notificationData[position].copy(statusViewData = viewData)
        currentSource?.invalidate()
    }

    private fun removeIfAccount(accountId: String) {
        // if the account we are displaying notifications from got blocked or muted, we can exit
        if (accountId == this.accountId) {
            viewModelScope.launch {
                _finish.emit(Unit)
            }
        }
    }

    fun remove(notification: NotificationViewData) {
        notificationData.remove(notification)
        currentSource?.invalidate()
    }

    fun reblog(reblog: Boolean, status: StatusViewData.Concrete) = viewModelScope.launch {
        timelineCases.reblog(status.actionableId, reblog).onFailure { t ->
            ifExpected(t) {
                Log.w(TAG, "Failed to reblog status " + status.actionableId, t)
            }
        }
    }

    fun favorite(favorite: Boolean, status: StatusViewData.Concrete) = viewModelScope.launch {
        timelineCases.favourite(status.actionableId, favorite).onFailure { t ->
            ifExpected(t) {
                Log.w(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun bookmark(bookmark: Boolean, status: StatusViewData.Concrete) = viewModelScope.launch {
        timelineCases.bookmark(status.actionableId, bookmark).onFailure { t ->
            ifExpected(t) {
                Log.w(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        updateStatusViewData(status.id) { it.copy(isExpanded = expanded) }
    }

    fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        updateStatusViewData(status.id) { it.copy(isShowingContent = isShowing) }
    }

    fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        updateStatusViewData(status.id) { it.copy(isCollapsed = isCollapsed) }
    }

    fun voteInPoll(choices: List<Int>, status: StatusViewData.Concrete) = viewModelScope.launch {
        val poll = status.status.actionableStatus.poll ?: run {
            Log.w(TAG, "No poll on status ${status.id}")
            return@launch
        }
        timelineCases.voteInPoll(status.actionableId, poll.id, choices).onFailure { t ->
            ifExpected(t) {
                Log.w(TAG, "Failed to vote in poll: " + status.actionableId, t)
            }
        }
    }

    suspend fun translate(status: StatusViewData.Concrete): NetworkResult<Unit> {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(translation = TranslationViewData.Loading)
        }
        return timelineCases.translate(status.actionableId)
            .map { translation ->
                updateStatusViewData(status.id) { viewData ->
                    viewData.copy(translation = TranslationViewData.Loaded(translation))
                }
            }
            .onFailure {
                updateStatusViewData(status.id) { viewData ->
                    viewData.copy(translation = null)
                }
            }
    }

    fun untranslate(status: StatusViewData.Concrete) {
        updateStatusViewData(status.id) { it.copy(translation = null) }
    }

    fun respondToFollowRequest(accept: Boolean, accountId: String, notification: NotificationViewData) {
        viewModelScope.launch {
            if (accept) {
                api.authorizeFollowRequest(accountId)
            } else {
                api.rejectFollowRequest(accountId)
            }.fold(
                onSuccess = {
                    // since the follow request has been responded, the notification can be deleted
                    remove(notification)
                },
                onFailure = { t ->
                    Log.w(TAG, "Failed to to respond to follow request from account id $accountId.", t)
                }
            )
        }
    }

    private fun updateStatusViewData(
        statusId: String,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val position = notificationData.indexOfFirst { it.asStatusOrNull()?.id == statusId }
        val statusViewData = notificationData.getOrNull(position)?.statusViewData ?: return
        notificationData[position] = notificationData[position].copy(statusViewData = updater(statusViewData))
        currentSource?.invalidate()
    }

    companion object {
        private const val TAG = "NotificationRequestsViewModel"
    }

    @AssistedFactory interface Factory {
        fun create(
            @Assisted("notificationRequestId") notificationRequestId: String,
            @Assisted("accountId") accountId: String
        ): NotificationRequestDetailsViewModel
    }
}
