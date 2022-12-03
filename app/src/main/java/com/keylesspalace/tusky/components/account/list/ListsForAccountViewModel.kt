/* Copyright 2022 kyori19
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

package com.keylesspalace.tusky.components.account.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.getOrThrow
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import at.connyduck.calladapter.networkresult.runCatching
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountListState(
    val list: MastoList,
    val includesAccount: Boolean,
)

data class ActionError(
    val error: Throwable,
    val type: Type,
    val listId: String,
) : Throwable(error) {
    enum class Type {
        ADD,
        REMOVE,
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListsForAccountViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
) : ViewModel() {

    private lateinit var accountId: String

    private val _states = MutableSharedFlow<List<AccountListState>>(1)
    val states: SharedFlow<List<AccountListState>> = _states

    private val _loadError = MutableSharedFlow<Throwable>(1)
    val loadError: SharedFlow<Throwable> = _loadError

    private val _actionError = MutableSharedFlow<ActionError>(1)
    val actionError: SharedFlow<ActionError> = _actionError

    fun setup(accountId: String) {
        this.accountId = accountId
    }

    fun load() {
        _loadError.resetReplayCache()
        viewModelScope.launch {
            runCatching {
                val (all, includes) = listOf(
                    async { mastodonApi.getLists() },
                    async { mastodonApi.getListsIncludesAccount(accountId) },
                ).awaitAll()

                _states.emit(
                    all.getOrThrow().map { list ->
                        AccountListState(
                            list = list,
                            includesAccount = includes.getOrThrow().any { it.id == list.id },
                        )
                    }
                )
            }
                .onFailure {
                    _loadError.emit(it)
                }
        }
    }

    fun addAccountToList(listId: String) {
        _actionError.resetReplayCache()
        viewModelScope.launch {
            mastodonApi.addAccountToList(listId, listOf(accountId))
                .onSuccess {
                    _states.emit(
                        _states.first().map { state ->
                            if (state.list.id == listId) {
                                state.copy(includesAccount = true)
                            } else {
                                state
                            }
                        }
                    )
                }
                .onFailure {
                    _actionError.emit(ActionError(it, ActionError.Type.ADD, listId))
                }
        }
    }

    fun removeAccountFromList(listId: String) {
        _actionError.resetReplayCache()
        viewModelScope.launch {
            mastodonApi.deleteAccountFromList(listId, listOf(accountId))
                .onSuccess {
                    _states.emit(
                        _states.first().map { state ->
                            if (state.list.id == listId) {
                                state.copy(includesAccount = false)
                            } else {
                                state
                            }
                        }
                    )
                }
                .onFailure {
                    _actionError.emit(ActionError(it, ActionError.Type.REMOVE, listId))
                }
        }
    }
}
