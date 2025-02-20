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

package com.keylesspalace.tusky.components.notifications.requests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.entity.NotificationRequest
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.NotificationPolicyUsecase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationRequestsViewModel @Inject constructor(
    private val api: MastodonApi,
    private val eventHub: EventHub,
    private val notificationPolicyUsecase: NotificationPolicyUsecase
) : ViewModel() {

    var currentSource: NotificationRequestsPagingSource? = null

    val requestData: MutableList<NotificationRequest> = mutableListOf()

    var nextKey: String? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20
        ),
        remoteMediator = NotificationRequestsRemoteMediator(api, this),
        pagingSourceFactory = {
            NotificationRequestsPagingSource(
                requests = requestData,
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

    init {
        viewModelScope.launch {
            eventHub.events
                .collect { event ->
                    when (event) {
                        is BlockEvent -> removeAllByAccount(event.accountId)
                        is MuteEvent -> removeAllByAccount(event.accountId)
                    }
                }
        }
    }

    fun acceptNotificationRequest(id: String) {
        viewModelScope.launch {
            api.acceptNotificationRequest(id).fold({
                removeNotificationRequest(id)
            }, { error ->
                Log.w(TAG, "failed to dismiss notifications request", error)
                _error.emit(error)
            })
        }
    }

    fun dismissNotificationRequest(id: String) {
        viewModelScope.launch {
            api.dismissNotificationRequest(id).fold({
                removeNotificationRequest(id)
            }, { error ->
                Log.w(TAG, "failed to dismiss notifications request", error)
                _error.emit(error)
            })
        }
    }

    fun removeNotificationRequest(id: String) {
        requestData.forEach { request ->
            if (request.id == id) {
                request.notificationsCount.toIntOrNull()?.let { notificationsCount ->
                    viewModelScope.launch {
                        notificationPolicyUsecase.updateCounts(notificationsCount)
                    }
                }
            }
        }
        requestData.removeAll { request -> request.id == id }
        currentSource?.invalidate()
    }

    private fun removeAllByAccount(accountId: String) {
        requestData.removeAll { request -> request.account.id == accountId }
        currentSource?.invalidate()
    }

    companion object {
        private const val TAG = "NotificationRequestsViewModel"
    }
}
