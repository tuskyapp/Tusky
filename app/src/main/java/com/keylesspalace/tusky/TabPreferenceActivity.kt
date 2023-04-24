/* Copyright 2019 Conny Duck
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

package com.keylesspalace.tusky

import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.adapter.ItemInteractionListener
import com.keylesspalace.tusky.di.Injectable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TabPreferenceActivity :
    OrderableListPreferenceActivity(),
    Injectable,
    ItemInteractionListener {

    override fun initializeList(): List<ScreenData> {
        return accountManager.activeAccount?.tabPreferences.orEmpty()
    }

    override fun saveList(list: List<ScreenData>) {
        accountManager.activeAccount?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                it.tabPreferences = list
                accountManager.saveAccount(it)
            }
        }
        screensChanged = true
    }

    override fun getMinCount(): Int = MIN_SCREEN_COUNT

    override fun getMaxCount(): Int = MAX_SCREEN_COUNT

    override fun getActivityTitle(): CharSequence = getString(R.string.title_tab_preferences)

    companion object {
        private const val MIN_SCREEN_COUNT = 2
        private const val MAX_SCREEN_COUNT = 5
    }
}
