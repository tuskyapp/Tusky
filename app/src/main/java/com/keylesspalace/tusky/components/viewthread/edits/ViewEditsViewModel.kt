/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread.edits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.entity.StatusEdit
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ViewEditsViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {

    private val _uiState: MutableStateFlow<EditsUiState> = MutableStateFlow(EditsUiState.Initial)
    val uiState: StateFlow<EditsUiState> = _uiState.asStateFlow()

    fun loadEdits(statusId: String, force: Boolean = false, refreshing: Boolean = false) {
        if (!force && _uiState.value !is EditsUiState.Initial) return

        if (refreshing) {
            _uiState.value = EditsUiState.Refreshing
        } else {
            _uiState.value = EditsUiState.Loading
        }

        viewModelScope.launch {
            api.statusEdits(statusId).fold(
                { edits ->
                    val sortedEdits = edits.sortedBy { edit -> edit.createdAt }.reversed()
                    _uiState.value = EditsUiState.Success(sortedEdits)
                },
                { throwable ->
                    _uiState.value = EditsUiState.Error(throwable)
                }
            )
        }
    }
}

sealed interface EditsUiState {
    object Initial : EditsUiState
    object Loading : EditsUiState

    // "Refreshing" state is necessary, otherwise a refresh state transition is Success -> Success,
    // and state flows don't emit repeated states, so the UI never updates.
    object Refreshing : EditsUiState
    class Error(val throwable: Throwable) : EditsUiState
    data class Success(
        val edits: List<StatusEdit>
    ) : EditsUiState
}
