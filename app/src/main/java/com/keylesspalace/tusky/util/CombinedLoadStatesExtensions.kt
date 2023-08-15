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

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.keylesspalace.tusky.util.PresentationState.INITIAL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan

/**
 * Each [CombinedLoadStates] state does not contain enough information to understand the actual
 * state unless previous states have been observed.
 *
 * This tracks those states and provides a [PresentationState] that describes whether the most
 * recent refresh has presented the data via the associated adapter.
 */
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

            SOURCE_LOADING -> when (loadState.refresh) {
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
 * @return Flow that combines the [CombinedLoadStates] with its associated [PresentationState].
 */
fun Flow<CombinedLoadStates>.withPresentationState(): Flow<Pair<CombinedLoadStates, PresentationState>> {
    val presentationStateFlow = scan(INITIAL) { state, loadState ->
        state.next(loadState)
    }
        .distinctUntilChanged()

    return this.combine(presentationStateFlow) { loadState, presentationState ->
        Pair(loadState, presentationState)
    }
}

/**
 * Each [CombinedLoadStates] state does not contain enough information to understand the actual
 * state unless previous states have been observed.
 *
 * This tracks those states and provides a [RefreshState] that describes whether the most recent
 * [Refresh][androidx.paging.PagingSource.LoadParams.Refresh] and its associated first
 * [Prepend][androidx.paging.PagingSource.LoadParams.Prepend] operation has completed.
 */
enum class RefreshState {
    /** No active refresh, waiting for one to start */
    WAITING_FOR_REFRESH,

    /** A refresh is underway */
    ACTIVE_REFRESH,

    /** A refresh has completed, there may be a followup prepend operations */
    REFRESH_COMPLETE,

    /** The first prepend is underway */
    ACTIVE_PREPEND,

    /** The first prepend after a refresh has completed. There may be followup prepend operations */
    PREPEND_COMPLETE,

    /** A refresh or prepend operation was [LoadState.Error] */
    ERROR;

    fun next(loadState: CombinedLoadStates): RefreshState {
        if (loadState.refresh is LoadState.Error) return ERROR
        if (loadState.prepend is LoadState.Error) return ERROR

        return when (this) {
            WAITING_FOR_REFRESH -> when (loadState.refresh) {
                is LoadState.Loading -> ACTIVE_REFRESH
                else -> this
            }
            ACTIVE_REFRESH -> when (loadState.refresh) {
                is LoadState.NotLoading -> when (loadState.prepend) {
                    is LoadState.Loading -> REFRESH_COMPLETE
                    // If prepend.endOfPaginationReached then the prepend is complete too.
                    // Otherwise, wait for the prepend to finish.
                    is LoadState.NotLoading -> if (loadState.prepend.endOfPaginationReached) {
                        PREPEND_COMPLETE
                    } else {
                        REFRESH_COMPLETE
                    }
                    else -> this
                }
                else -> this
            }
            REFRESH_COMPLETE -> when (loadState.prepend) {
                is LoadState.Loading -> ACTIVE_PREPEND
                else -> this
            }
            ACTIVE_PREPEND -> when (loadState.prepend) {
                is LoadState.NotLoading -> PREPEND_COMPLETE
                else -> this
            }
            PREPEND_COMPLETE -> when (loadState.refresh) {
                is LoadState.Loading -> ACTIVE_REFRESH
                else -> this
            }
            ERROR -> WAITING_FOR_REFRESH.next(loadState)
        }
    }
}

fun Flow<CombinedLoadStates>.asRefreshState(): Flow<RefreshState> {
    return scan(RefreshState.WAITING_FOR_REFRESH) { state, loadState ->
        state.next(loadState)
    }
        .distinctUntilChanged()
}

/**
 * Debug helper that generates a string showing the effective difference between two [CombinedLoadStates].
 *
 * @param prev the value to compare against
 * @return A (possibly multi-line) string showing the fields that differed
 */
fun CombinedLoadStates.diff(prev: CombinedLoadStates?): String {
    prev ?: return ""

    val result = mutableListOf<String>()

    if (prev.refresh != refresh) {
        result.add(".refresh ${prev.refresh} -> $refresh")
    }
    if (prev.source.refresh != source.refresh) {
        result.add("\n  .source.refresh ${prev.source.refresh} -> ${source.refresh}")
    }
    if (prev.mediator?.refresh != mediator?.refresh) {
        result.add("\n  .mediator.refresh ${prev.mediator?.refresh} -> ${mediator?.refresh}")
    }

    if (prev.prepend != prepend) {
        result.add(".prepend ${prev.prepend} -> $prepend")
    }
    if (prev.source.prepend != source.prepend) {
        result.add("\n  .source.prepend ${prev.source.prepend} -> ${source.prepend}")
    }
    if (prev.mediator?.prepend != mediator?.prepend) {
        result.add("\n  .mediator.prepend ${prev.mediator?.prepend} -> ${mediator?.prepend}")
    }

    if (prev.append != append) {
        result.add(".append ${prev.append} -> $append")
    }
    if (prev.source.append != source.append) {
        result.add("\n  .source.append ${prev.source.append} -> ${source.append}")
    }
    if (prev.mediator?.append != mediator?.append) {
        result.add("\n  .mediator.append ${prev.mediator?.append} -> ${mediator?.append}")
    }

    return result.joinToString("\n")
}
