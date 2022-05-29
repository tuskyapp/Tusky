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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.replacedFirstWhich
import com.keylesspalace.tusky.util.withoutFirstWhich
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import javax.inject.Inject

internal class ListsViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {
    enum class LoadingState {
        INITIAL, LOADING, LOADED, ERROR_NETWORK, ERROR_OTHER
    }

    enum class Event {
        CREATE_ERROR, DELETE_ERROR, RENAME_ERROR
    }

    data class State(val lists: List<MastoList>, val loadingState: LoadingState)

    val state: Flow<State> get() = _state
    val events: Flow<Event> get() = _events
    private val _state = MutableStateFlow(State(listOf(), LoadingState.INITIAL))
    private val _events = MutableSharedFlow<Event>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun retryLoading() {
        loadIfNeeded()
    }

    private fun loadIfNeeded() {
        val state = _state.value
        if (state.loadingState == LoadingState.LOADING || state.lists.isNotEmpty()) return
        updateState {
            copy(loadingState = LoadingState.LOADING)
        }

        viewModelScope.launch {
            api.getLists().fold(
                { lists ->
                    updateState {
                        copy(
                            lists = lists,
                            loadingState = LoadingState.LOADED
                        )
                    }
                },
                { err ->
                    updateState {
                        copy(
                            loadingState = if (err is IOException || err is ConnectException)
                                LoadingState.ERROR_NETWORK else LoadingState.ERROR_OTHER
                        )
                    }
                }
            )
        }
    }

    fun createNewList(listName: String) {
        viewModelScope.launch {
            api.createList(listName).fold(
                { list ->
                    updateState {
                        copy(lists = lists + list)
                    }
                },
                {
                    sendEvent(Event.CREATE_ERROR)
                }
            )
        }
    }

    fun renameList(listId: String, listName: String) {
        viewModelScope.launch {
            api.updateList(listId, listName).fold(
                { list ->
                    updateState {
                        copy(lists = lists.replacedFirstWhich(list) { it.id == listId })
                    }
                },
                {
                    sendEvent(Event.RENAME_ERROR)
                }
            )
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            api.deleteList(listId).fold(
                {
                    updateState {
                        copy(lists = lists.withoutFirstWhich { it.id == listId })
                    }
                },
                {
                    sendEvent(Event.DELETE_ERROR)
                }
            )
        }
    }

    private inline fun updateState(crossinline fn: State.() -> State) {
        _state.value = fn(_state.value)
    }

    private suspend fun sendEvent(event: Event) {
        _events.emit(event)
    }
}
