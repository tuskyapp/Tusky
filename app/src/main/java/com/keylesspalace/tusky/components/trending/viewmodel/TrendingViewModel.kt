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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.TrendingViewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okio.IOException
import javax.inject.Inject

class TrendingViewModel @Inject constructor(
    private val mastodonApi: MastodonApi
) : ViewModel() {
    enum class LoadingState {
        INITIAL, LOADING, LOADED, ERROR_NETWORK, ERROR_OTHER
    }

    data class TrendingUiState(
        val trendingViewData: List<TrendingViewData>,
        val loadingState: LoadingState
    )

    val uiState: Flow<TrendingUiState> get() = _uiState
    private val _uiState = MutableStateFlow(TrendingUiState(listOf(), LoadingState.INITIAL))

    init {
        invalidate()
    }

    /** Triggered when currently displayed data must be reloaded. */
    fun invalidate() = viewModelScope.launch {
        _uiState.value = TrendingUiState(emptyList(), LoadingState.LOADING)

        try {
            val response = mastodonApi.trendingTags()
            if (!response.isSuccessful) {
                _uiState.value = TrendingUiState(emptyList(), LoadingState.ERROR_NETWORK)
                return@launch
            }
            _uiState.value = TrendingUiState(
                response.body()!!.map { it.toViewData() },
                LoadingState.LOADED
            )
        } catch (e: IOException) {
            _uiState.value = TrendingUiState(emptyList(), LoadingState.ERROR_NETWORK)
        } catch (e: Exception) {
            _uiState.value = TrendingUiState(emptyList(), LoadingState.ERROR_OTHER)
        }
    }

    companion object {
        private const val TAG = "TrendingViewModel"
    }
}
