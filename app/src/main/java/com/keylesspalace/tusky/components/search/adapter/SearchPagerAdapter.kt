/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.search.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.keylesspalace.tusky.components.search.fragments.SearchAccountsFragment
import com.keylesspalace.tusky.components.search.fragments.SearchHashtagsFragment
import com.keylesspalace.tusky.components.search.fragments.SearchStatusesFragment

class SearchPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SearchStatusesFragment.newInstance()
            1 -> SearchAccountsFragment.newInstance()
            2 -> SearchHashtagsFragment.newInstance()
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    override fun getItemCount() = 3

}