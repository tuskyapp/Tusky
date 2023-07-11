/* Copyright 2023 Tusky Contributors
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

package com.keylesspalace.tusky.components.trending.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.end
import com.keylesspalace.tusky.entity.start
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.TrendingViewData
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class TrendingViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub
) : ViewModel() {
    enum class LoadingState {
        INITIAL, LOADING, REFRESHING, LOADED, ERROR_NETWORK, ERROR_OTHER
    }

    data class TrendingUiState(
        val trendingViewData: List<TrendingViewData>,
        val loadingState: LoadingState
    )

    val uiState: Flow<TrendingUiState> get() = _uiState
    private val _uiState = MutableStateFlow(TrendingUiState(listOf(), LoadingState.INITIAL))

    init {
        invalidate()

        // Collect PreferenceChangedEvent, FiltersActivity creates them when a filter is created
        // or deleted. Unfortunately, there's nothing in the event to determine if it's a filter
        // that was modified, so refresh on every preference change.
        viewModelScope.launch {
            eventHub.events
                .filterIsInstance<PreferenceChangedEvent>()
                .collect {
                    invalidate()
                }
        }
    }

    /**
     * Invalidate the current list of trending tags and fetch a new list.
     *
     * A tag is excluded if it is filtered by the user on their home timeline.
     */
    fun invalidate(refresh: Boolean = false) = viewModelScope.launch {
        if (refresh) {
            _uiState.value = TrendingUiState(emptyList(), LoadingState.REFRESHING)
        } else {
            _uiState.value = TrendingUiState(emptyList(), LoadingState.LOADING)
        }

        val deferredFilters = async { mastodonApi.getFilters() }

        mastodonApi.trendingTags().fold(
            { tagResponse ->
                val homeFilters = deferredFilters.await().getOrNull()?.filter { filter ->
                    filter.context.contains(Filter.Kind.HOME.kind)
                }
                val tags = tagResponse
                    .filter { tag ->
                        homeFilters?.none { filter ->
                            filter.keywords.any { keyword -> keyword.keyword.equals(tag.name, ignoreCase = true) }
                        } ?: false
                    }
                    .sortedByDescending { tag -> tag.history.sumOf { it.uses.toLongOrNull() ?: 0 } }
                    .toViewData()

                val firstTag = tagResponse.first()
                val header = TrendingViewData.Header(firstTag.start(), firstTag.end())

                _uiState.value = TrendingUiState(listOf(header) + tags, LoadingState.LOADED)
            },
            { error ->
                Log.w(TAG, "failed loading trending tags", error)
                if (error is IOException) {
                    _uiState.value = TrendingUiState(emptyList(), LoadingState.ERROR_NETWORK)
                } else {
                    _uiState.value = TrendingUiState(emptyList(), LoadingState.ERROR_OTHER)
                }
            }
        )
    }

    companion object {
        private const val TAG = "TrendingViewModel"
    }
}
