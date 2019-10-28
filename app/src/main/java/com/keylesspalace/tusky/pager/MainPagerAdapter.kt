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

import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.keylesspalace.tusky.TabData
import java.lang.ref.WeakReference

class MainPagerAdapter(val tabs: List<TabData>, activity: FragmentActivity) : FragmentStateAdapter(activity) {
    private val fragments = SparseArray<WeakReference<Fragment>>(tabs.size)

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        val fragment = tab.fragment(tab.arguments)
        fragments.put(position, WeakReference(fragment))
        return fragment
    }

    override fun getItemCount() = tabs.size

    fun getFragment(position: Int): Fragment? = fragments[position].get()
}
