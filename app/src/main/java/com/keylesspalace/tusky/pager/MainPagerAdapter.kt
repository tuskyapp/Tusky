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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.keylesspalace.tusky.ScreenData
import com.keylesspalace.tusky.TabScreenData
import com.keylesspalace.tusky.util.CustomFragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : CustomFragmentStateAdapter(activity) {

    var tabs: List<ScreenData> = emptyList()
        set(value) {
            field = value.filterIsInstance<TabScreenData>()
        }

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        val action = when (tab) {
            is TabScreenData -> tab.fragmentAction
            else -> throw IllegalStateException("Fragment Adapter cannot contain an intent action")
        }
        return action(tab.arguments)
    }

    override fun getItemCount() = tabs.size
}
