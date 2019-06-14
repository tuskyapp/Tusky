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

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.components.search.fragments.SearchStatusFragment

class SearchPagerAdapter(private val context: Context, manager: FragmentManager) : FragmentPagerAdapter(manager) {
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> SearchStatusFragment.newInstance(SearchType.Status)
            1 -> SearchStatusFragment.newInstance(SearchType.Account)
            2 -> SearchStatusFragment.newInstance(SearchType.Hashtag)
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            0 -> context.getString(R.string.title_statuses)
            1 -> context.getString(R.string.title_accounts)
            2 -> context.getString(R.string.title_hashtags_dialog)
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    override fun getCount(): Int = 3
}