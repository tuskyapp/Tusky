/* Copyright 2025 Tusky Contributors.
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

package com.keylesspalace.tusky.components.accountlist

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.network.MastodonApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AccountListViewModel.Factory::class)
class AccountListViewModel @AssistedInject constructor(
    private val api: MastodonApi,
    @Assisted("type") val type: AccountListActivity.Type,
    @Assisted("id") val accountId: String?
) : ViewModel() {

    private val factory = InvalidatingPagingSourceFactory {
        AccountListPagingSource(accounts.toList(), nextKey)
    }

    @OptIn(ExperimentalPagingApi::class)
    val accountPager = Pager(
        config = PagingConfig(40),
        remoteMediator = AccountListRemoteMediator(api, this, fetchRelationships = type == AccountListActivity.Type.MUTES),
        pagingSourceFactory = factory
    ).flow
        .cachedIn(viewModelScope)

    val accounts: MutableList<AccountViewData> = mutableListOf()
    var nextKey: String? = null

    private val _uiEvents = MutableStateFlow<List<SnackbarEvent>>(emptyList())
    val uiEvents: Flow<SnackbarEvent> = _uiEvents.map { it.firstOrNull() }.filterNotNull().distinctUntilChanged()

    fun invalidate() {
        factory.invalidate()
    }

    // this is called by the mute notification toggle
    fun mute(accountId: String, notifications: Boolean) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.muteAccount(accountId, notifications).onFailure { e ->
                sendEvent(
                    SnackbarEvent(
                        message = R.string.mute_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { mute(accountId, notifications) }
                    )
                )
            }
        }
    }

    // this is called when unmuting is undone
    private fun remute(accountViewData: AccountViewData) {
        viewModelScope.launch {
            api.muteAccount(accountViewData.id).fold({
                accounts.add(accountViewData)
                invalidate()
            }, { e ->
                sendEvent(
                    SnackbarEvent(
                        message = R.string.mute_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { block(accountViewData) }
                    )
                )
            })
        }
    }

    fun unmute(accountId: String) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.unmuteAccount(accountId).fold({
                accounts.removeIf { it.id == accountId }
                invalidate()
                sendEvent(
                    SnackbarEvent(
                        message = R.string.unmute_success,
                        user = "@${accountViewData.account.username}",
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { remute(accountViewData) }
                    )
                )
            }, { error ->
                sendEvent(
                    SnackbarEvent(
                        message = R.string.unmute_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = error,
                        actionText = R.string.action_retry,
                        action = { unmute(accountId) }
                    )
                )
            })
        }
    }

    fun unblock(accountId: String) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.unblockAccount(accountId).fold({
                accounts.removeIf { it.id == accountId }
                invalidate()
                sendEvent(
                    SnackbarEvent(
                        message = R.string.unblock_success,
                        user = "@${accountViewData.account.username}",
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { block(accountViewData) }
                    )
                )
            }, { e ->
                sendEvent(
                    SnackbarEvent(
                        message = R.string.unblock_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { unblock(accountId) }
                    )
                )
            })
        }
    }

    private fun block(accountViewData: AccountViewData) {
        viewModelScope.launch {
            api.blockAccount(accountViewData.id).fold({
                accounts.add(accountViewData)
                invalidate()
            }, { e ->
                sendEvent(
                    SnackbarEvent(
                        message = R.string.block_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { block(accountViewData) }
                    )
                )
            })
        }
    }

    fun respondToFollowRequest(accept: Boolean, accountId: String) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            if (accept) {
                api.authorizeFollowRequest(accountId)
            } else {
                api.rejectFollowRequest(accountId)
            }.fold({
                accounts.removeIf { it.id == accountId }
                invalidate()
            }, { e ->
                sendEvent(
                    SnackbarEvent(
                        message = if (accept) R.string.accept_follow_request_failure else R.string.reject_follow_request_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { respondToFollowRequest(accept, accountId) }
                    )
                )
            })
        }
    }

    fun consumeEvent(event: SnackbarEvent) {
        println("event consumed $event")
        _uiEvents.update { uiEvents ->
            uiEvents - event
        }
    }

    private fun sendEvent(event: SnackbarEvent) {
        println("event sent $event")
        _uiEvents.update { uiEvents ->
            uiEvents + event
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("type") type: AccountListActivity.Type,
            @Assisted("id") accountId: String?
        ): AccountListViewModel
    }
}

class SnackbarEvent(
    @StringRes val message: Int,
    val user: String,
    @StringRes val actionText: Int,
    val action: (View) -> Unit,
    val throwable: Throwable? = null
)
