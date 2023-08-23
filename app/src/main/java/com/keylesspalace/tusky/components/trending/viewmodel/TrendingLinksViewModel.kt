/*
 * Copyright 2023 Tusky Contributors
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

package com.keylesspalace.tusky.components.trending.viewmodel

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.trending.TrendingLinksRepository
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.TrendsLink
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.throttleFirst
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

sealed class UiAction

sealed class InfallibleUiAction : UiAction() {
    object Reload : InfallibleUiAction()
}

sealed class LoadState {
    object Initial : LoadState()
    object Loading : LoadState()
    data class Success(val data: List<TrendsLink>) : LoadState()
    data class Error(val throwable: Throwable) : LoadState()
}

@OptIn(ExperimentalTime::class)
class TrendingLinksViewModel @Inject constructor(
    private val repository: TrendingLinksRepository,
    preferences: SharedPreferences,
    accountManager: AccountManager,
    private val eventHub: EventHub
) : ViewModel() {
    val account = accountManager.activeAccount!!

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Initial)
    val loadState = _loadState.asStateFlow()

    private val _statusDisplayOptions = MutableStateFlow(
        StatusDisplayOptions.from(preferences, account)
    )
    val statusDisplayOptions = _statusDisplayOptions.asStateFlow()

    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    init {
        viewModelScope.launch {
            eventHub.events
                .filterIsInstance<PreferenceChangedEvent>()
                .filter { StatusDisplayOptions.prefKeys.contains(it.preferenceKey) }
                .map { _statusDisplayOptions.value.make(preferences, it.preferenceKey, account) }
                .collect { _statusDisplayOptions.emit(it) }
        }

        viewModelScope.launch {
            uiAction
                .throttleFirst(THROTTLE_TIMEOUT)
                .filterIsInstance<InfallibleUiAction.Reload>()
                .onEach { invalidate() }
                .collect()
        }
    }

    private fun invalidate() = viewModelScope.launch {
        _loadState.update { LoadState.Loading }
        val response = repository.getTrendingLinks()
        response.fold(
            { list -> _loadState.update { LoadState.Success(list) } },
            { throwable -> _loadState.update { LoadState.Error(throwable) } }
        )
    }

    companion object {
        @SuppressLint("unused")
        private const val TAG = "TrendingLinksViewModel"
        private val THROTTLE_TIMEOUT = 500.milliseconds
    }
}
