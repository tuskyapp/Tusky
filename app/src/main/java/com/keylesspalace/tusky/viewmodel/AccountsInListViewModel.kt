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
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrDefault
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.withoutFirstWhich
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class State(
    val accounts: Result<List<TimelineAccount>>,
    val searchResult: List<TimelineAccount>?
)

@HiltViewModel
class AccountsInListViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {

    val state: Flow<State> get() = _state
    private val _state = MutableStateFlow(
        State(
            accounts = Result.success(emptyList()),
            searchResult = null
        )
    )

    fun load(listId: String) {
        val state = _state.value
        if (state.accounts.isFailure || state.accounts.getOrThrow().isEmpty()) {
            viewModelScope.launch {
                val accounts = api.getAccountsInList(listId, 0)
                _state.update { it.copy(accounts = accounts.toResult()) }
            }
        }
    }

    fun addAccountToList(listId: String, account: TimelineAccount) {
        viewModelScope.launch {
            api.addAccountToList(listId, listOf(account.id))
                .fold(
                    onSuccess = {
                        _state.update { state ->
                            state.copy(accounts = state.accounts.map { it + account })
                        }
                    },
                    onFailure = {
                        Log.i(
                            AccountsInListViewModel::class.java.simpleName,
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
                    onSuccess = {
                        _state.update { state ->
                            state.copy(
                                accounts = state.accounts.map { accounts ->
                                    accounts.withoutFirstWhich { it.id == accountId }
                                }
                            )
                        }
                    },
                    onFailure = {
                        Log.i(
                            AccountsInListViewModel::class.java.simpleName,
                            "Failed to remove account from list: $accountId"
                        )
                    }
                )
        }
    }

    private val currentQuery = MutableStateFlow("")

    fun search(query: String) {
        currentQuery.value = query
    }

    init {
        viewModelScope.launch {
            // Use collectLatest to automatically cancel the previous search
            currentQuery.collectLatest { query ->
                val searchResult = when {
                    query.isEmpty() -> null
                    query.isBlank() -> emptyList()
                    else -> api.searchAccounts(query, null, 10, true)
                        .getOrDefault(emptyList())
                }
                _state.update { it.copy(searchResult = searchResult) }
            }
        }
    }

    private fun <T> NetworkResult<T>.toResult(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
