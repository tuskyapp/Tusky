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
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.TrendingTag
import com.keylesspalace.tusky.usecase.TrendingCases
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.TrendingViewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okio.IOException
import javax.inject.Inject

class TrendingViewModel @Inject constructor(
    private val trendingCases: TrendingCases,
    private val accountManager: AccountManager,
) : ViewModel() {
    enum class LoadingState {
        INITIAL, LOADING, LOADED, ERROR_NETWORK, ERROR_OTHER
    }

    data class State(val trendingViewData: List<TrendingViewData>, val loadingState: LoadingState)

    val state: Flow<State> get() = _state
    private val _state = MutableStateFlow(State(listOf(), LoadingState.INITIAL))

    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoilers = false

    fun init() {
        this.alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        this.alwaysOpenSpoilers = accountManager.activeAccount!!.alwaysOpenSpoiler

        viewModelScope.launch {
            invalidate()
        }
    }

    suspend fun trendingTags(): List<TrendingTag> {
        return trendingCases.trendingTags()
    }

    /** Triggered when currently displayed data must be reloaded. */
    suspend fun invalidate() {
        _state.value = State(listOf(), LoadingState.LOADING)

        try {
            val trending = trendingTags()
            val viewData = trending.map { it.toViewData() }
            _state.value = State(viewData, LoadingState.LOADED)
        } catch (e: IOException) {
            _state.value = State(listOf(), LoadingState.ERROR_NETWORK)
        } catch (e: Exception) {
            _state.value = State(listOf(), LoadingState.ERROR_OTHER)
        }
    }

    companion object {
        private const val TAG = "TrendingVM"
    }
}
