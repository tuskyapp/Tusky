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
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.util.PresentationState.INITIAL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 * The state of the refresh from the user's perspective. A refresh is "complete" for a user if
 * the refresh has completed, **and** the first prepend triggered by that refresh has also
 * completed.
 *
 * This means that new data has been loaded and (if the prepend found new data) the user can
 * start scrolling up to see it. Any progress indicators can be removed, and the UI can scroll
 * to disclose new content.
 */
enum class UserRefreshState {
    /** No active refresh, waiting for one to start */
    WAITING,

    /** A refresh (and possibly the first prepend) is underway */
    ACTIVE,

    /** The refresh and the first prepend after a refresh has completed */
    COMPLETE,

    /** A refresh or prepend operation was [LoadState.Error] */
    ERROR;
}

/**
 * Each [CombinedLoadStates] state does not contain enough information to understand the actual
 * state unless previous states have been observed.
 *
 * This tracks those states and provides a [UserRefreshState] that describes whether the most recent
 * [Refresh][androidx.paging.PagingSource.LoadParams.Refresh] and its associated first
 * [Prepend][androidx.paging.PagingSource.LoadParams.Prepend] operation has completed.
 */
fun Flow<CombinedLoadStates>.asRefreshState(): Flow<UserRefreshState> {
    // Can't use CombinedLoadStates.refresh and .prepend on their own. In testing I've observed
    // situations where:
    //
    // - the refresh completes before the prepend starts
    // - the prepend starts before the refresh completes
    // - the prepend *ends* before the refresh completes (but after the refresh starts)
    //
    // So you need to track the state of both the refresh and the prepend actions, and merge them
    // in to a single state that answers the question "Has the refresh, and the first prepend
    // started by that refresh, finished?"
    //
    // In addition, a prepend operation might involve both the mediator and the source, or only
    // one of them. Since loadState.prepend tracks the mediator property this means a prepend that
    // only modifies loadState.source will not be reflected in loadState.prepend.
    //
    // So the code also has to track whether the prepend transition was initiated by the mediator
    // or the source property, and look for the end of the transition on the same property.

    /** The state of the "refresh" portion of the user refresh */
    var refresh = UserRefreshState.WAITING

    /** The state of the "prepend" portion of the user refresh */
    var prepend = UserRefreshState.WAITING

    /** True if the state of the prepend portion is derived from the mediator property */
    var usePrependMediator = false

    var previousLoadState: CombinedLoadStates? = null

    return map { loadState ->
        // Debug helper, show the differences between successive load states.
        if (BuildConfig.DEBUG) {
            previousLoadState?.let {
                val loadStateDiff = loadState.diff(previousLoadState)
                Log.d("RefreshState", "Current state: $refresh $prepend")
                if (loadStateDiff.isNotEmpty()) Log.d("RefreshState", loadStateDiff)
            }
            previousLoadState = loadState
        }

        // Bail early on errors
        if (loadState.refresh is LoadState.Error || loadState.prepend is LoadState.Error) {
            refresh = UserRefreshState.WAITING
            prepend = UserRefreshState.WAITING
            return@map UserRefreshState.ERROR
        }

        // Handling loadState.refresh is straightforward
        refresh = when (loadState.refresh) {
            is LoadState.Loading -> if (refresh == UserRefreshState.WAITING) UserRefreshState.ACTIVE else refresh
            is LoadState.NotLoading -> if (refresh == UserRefreshState.ACTIVE) UserRefreshState.COMPLETE else refresh
            else -> { throw IllegalStateException("can't happen, LoadState.Error is already handled") }
        }

        // Prepend can only transition to active if there is an active or complete refresh
        // (i.e., the refresh is not WAITING).
        if (refresh != UserRefreshState.WAITING && prepend == UserRefreshState.WAITING) {
            if (loadState.mediator?.prepend is LoadState.Loading) {
                usePrependMediator = true
                prepend = UserRefreshState.ACTIVE
            }
            if (loadState.source.prepend is LoadState.Loading) {
                usePrependMediator = false
                prepend = UserRefreshState.ACTIVE
            }
        }

        if (prepend == UserRefreshState.ACTIVE) {
            if (usePrependMediator && loadState.mediator?.prepend is LoadState.NotLoading) {
                prepend = UserRefreshState.COMPLETE
            }
            if (!usePrependMediator && loadState.source.prepend is LoadState.NotLoading) {
                prepend = UserRefreshState.COMPLETE
            }
        }

        // Determine the new user refresh state by combining the refresh and prepend states
        //
        // - If refresh and prepend are identical use the refresh value
        // - If refresh is WAITING then the state is WAITING (waiting for a refresh implies waiting
        //   for a prepend too)
        // - Otherwise, one of them is active (doesn't matter which), so the state is ACTIVE
        val newUserRefreshState = when (refresh) {
            prepend -> refresh
            UserRefreshState.WAITING -> UserRefreshState.WAITING
            else -> UserRefreshState.ACTIVE
        }

        // If the new state is COMPLETE reset the individual states back to WAITING, ready for
        // the next user refresh.
        if (newUserRefreshState == UserRefreshState.COMPLETE) {
            refresh = UserRefreshState.WAITING
            prepend = UserRefreshState.WAITING
        }

        return@map newUserRefreshState
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
        result.add("  .source.refresh ${prev.source.refresh} -> ${source.refresh}")
    }
    if (prev.mediator?.refresh != mediator?.refresh) {
        result.add("  .mediator.refresh ${prev.mediator?.refresh} -> ${mediator?.refresh}")
    }

    if (prev.prepend != prepend) {
        result.add(".prepend ${prev.prepend} -> $prepend")
    }
    if (prev.source.prepend != source.prepend) {
        result.add("  .source.prepend ${prev.source.prepend} -> ${source.prepend}")
    }
    if (prev.mediator?.prepend != mediator?.prepend) {
        result.add("  .mediator.prepend ${prev.mediator?.prepend} -> ${mediator?.prepend}")
    }

    if (prev.append != append) {
        result.add(".append ${prev.append} -> $append")
    }
    if (prev.source.append != source.append) {
        result.add("  .source.append ${prev.source.append} -> ${source.append}")
    }
    if (prev.mediator?.append != mediator?.append) {
        result.add("  .mediator.append ${prev.mediator?.append} -> ${mediator?.append}")
    }

    return result.joinToString("\n")
}
