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

package com.keylesspalace.tusky.util

import android.util.Log
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.keylesspalace.tusky.util.PresentationState.INITIAL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan

enum class PresentationState {
    /** Initial state, nothing is known about the load state */
    INITIAL,

    /** RemoteMediator is loading the first requested page of results */
    REMOTE_LOADING,

    /** PagingSource is loading the first requested page of results */
    SOURCE_LOADING,

    /** There was an error loading the first page of results */
    ERROR,

    /** The first request page of results is visible via the adapter */
    PRESENTED;

    /**
     * Take the next step in the PresentationState state machine, given [loadState]
     */
    fun next(loadState: CombinedLoadStates): PresentationState {
        if (loadState.mediator?.refresh is LoadState.Error) return ERROR
        if (loadState.source.refresh is LoadState.Error) return ERROR

        return when (this) {
            INITIAL -> when (loadState.mediator?.refresh) {
                is LoadState.Loading -> REMOTE_LOADING
                else -> this
            }

            REMOTE_LOADING -> when (loadState.source.refresh) {
                is LoadState.Loading -> SOURCE_LOADING
                else -> this
            }

            SOURCE_LOADING -> when (loadState.source.refresh) {
                is LoadState.NotLoading -> PRESENTED
                else -> this
            }

            ERROR -> INITIAL.next(loadState)

            PRESENTED -> when (loadState.mediator?.refresh) {
                is LoadState.Loading -> REMOTE_LOADING
                else -> this
            }
        }
    }
}

/**
 * [CombinedLoadStates] are stateful -- you can't fully interpret the meaning of the state unless
 * previous states have been observed. This tracks those states and provides a [PresentationState]
 * that describes whether the most recent refresh has presented the data via the associated adapter.
 *
 * @return Flow that combines the load state with its associated presentation state
 */
fun Flow<CombinedLoadStates>.withPresentationState(): Flow<Pair<CombinedLoadStates, PresentationState>> {
    val TAG = "WithPresentationState"

    val presentationStateFlow = scan(INITIAL) { state, loadState ->
        Log.d(TAG, "state: $state")
        Log.d(TAG, "loadState.mediator.refresh: ${loadState.mediator?.refresh}")
        Log.d(TAG, "loadState.source.refresh: ${loadState.source.refresh}")
        state.next(loadState)
    }
        .distinctUntilChanged()

    return this.combine(presentationStateFlow) { loadState, presentationState ->
        Pair(loadState, presentationState)
    }
}
