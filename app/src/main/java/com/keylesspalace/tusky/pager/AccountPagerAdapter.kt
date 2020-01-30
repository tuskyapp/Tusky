/* Copyright 2019 Tusky Contributors
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

import androidx.fragment.app.*

import com.keylesspalace.tusky.fragment.AccountMediaFragment
import com.keylesspalace.tusky.fragment.TimelineFragment
import com.keylesspalace.tusky.interfaces.RefreshableFragment

import com.keylesspalace.tusky.util.CustomFragmentStateAdapter

class AccountPagerAdapter(
        activity: FragmentActivity,
        private val accountId: String
) : CustomFragmentStateAdapter(activity) {

    override fun getItemCount() = TAB_COUNT

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TimelineFragment.newInstance(TimelineFragment.Kind.USER, accountId, false)
            1 -> TimelineFragment.newInstance(TimelineFragment.Kind.USER_WITH_REPLIES, accountId, false)
            2 -> TimelineFragment.newInstance(TimelineFragment.Kind.USER_PINNED, accountId, false)
            3 -> AccountMediaFragment.newInstance(accountId, false)
            else -> throw AssertionError("Page $position is out of AccountPagerAdapter bounds")
        }
    }

    fun refreshContent() {
        for (i in 0 until TAB_COUNT) {
            val fragment = getFragment(i)
            if (fragment != null && fragment is RefreshableFragment) {
                (fragment as RefreshableFragment).refreshContent()
            }
        }
    }

    companion object {
        private const val TAB_COUNT = 4
    }
}
