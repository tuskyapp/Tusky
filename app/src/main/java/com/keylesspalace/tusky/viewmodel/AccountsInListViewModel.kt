/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.Either.Left
import com.keylesspalace.tusky.util.Either.Right
import com.keylesspalace.tusky.util.withoutFirstWhich
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class State(val accounts: Either<Throwable, List<TimelineAccount>>, val searchResult: List<TimelineAccount>?)

class AccountsInListViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {

    val state: Flow<State> get() = _state
    private val _state = MutableStateFlow(State(Right(listOf()), null))

    fun load(listId: String) {
        val state = _state.value
        if (state.accounts.isLeft() || state.accounts.asRight().isEmpty()) {
            viewModelScope.launch {
                api.getAccountsInList(listId, 0).fold(
                    { accounts ->
                        updateState { copy(accounts = Right(accounts)) }
                    },
                    { e ->
                        updateState { copy(accounts = Left(e)) }
                    }
                )
            }
        }
    }

    fun addAccountToList(listId: String, account: TimelineAccount) {
        viewModelScope.launch {
            api.addAccountToList(listId, listOf(account.id))
                .fold(
                    {
                        updateState {
                            copy(accounts = accounts.map { it + account })
                        }
                    },
                    {
                        Log.i(
                            javaClass.simpleName,
                            "Failed to add account to list: ${account.username}"
                        )
                    }
                )
        }
    }

    fun deleteAccountFromList(listId: String, accountId: String) {
        viewModelScope.launch {
            api.deleteAccountFromList(listId, listOf(accountId))
                .fold(
                    {
                        updateState {
                            copy(
                                accounts = accounts.map { accounts ->
                                    accounts.withoutFirstWhich { it.id == accountId }
                                }
                            )
                        }
                    },
                    {
                        Log.i(
                            javaClass.simpleName,
                            "Failed to remove account from list: $accountId"
                        )
                    }
                )
        }
    }

    fun search(query: String) {
        when {
            query.isEmpty() -> updateState { copy(searchResult = null) }
            query.isBlank() -> updateState { copy(searchResult = listOf()) }
            else -> viewModelScope.launch {
                api.searchAccounts(query, null, 10, true)
                    .fold(
                        { result ->
                            updateState { copy(searchResult = result) }
                        },
                        {
                            updateState { copy(searchResult = listOf()) }
                        }
                    )
            }
        }
    }

    private inline fun updateState(crossinline fn: State.() -> State) {
        _state.value = fn(_state.value)
    }
}
