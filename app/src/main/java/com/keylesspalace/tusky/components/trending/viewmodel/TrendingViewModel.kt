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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class TrendingViewModel @Inject constructor(
    private val trendingCases: TrendingCases,
    protected val accountManager: AccountManager,
) : ViewModel() {

    val tags: MutableStateFlow<List<TrendingViewData.Tag>> = MutableStateFlow(emptyList())

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
        val trending = trendingTags()
        val viewData = trending.map { it.toViewData() }
        tags.emit(viewData)
    }

    companion object {
        private const val TAG = "TrendingVM"
    }
}
