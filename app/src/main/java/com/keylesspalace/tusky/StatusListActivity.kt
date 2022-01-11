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
 * see <https://www.gnu.org/licenses>. */

package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel.Kind
import com.keylesspalace.tusky.databinding.ActivityStatuslistBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class StatusListActivity : BottomSheetActivity(), HasAndroidInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    private val kind: Kind
        get() = Kind.valueOf(intent.getStringExtra(EXTRA_KIND)!!)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityStatuslistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        val title = if (kind == Kind.FAVOURITES) {
            R.string.title_favourites
        } else {
            R.string.title_bookmarks
        }

        supportActionBar?.run {
            setTitle(title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        supportFragmentManager.commit {
            val fragment = TimelineFragment.newInstance(kind)
            replace(R.id.fragment_container, fragment)
        }
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {

        private const val EXTRA_KIND = "kind"

        @JvmStatic
        fun newFavouritesIntent(context: Context) =
            Intent(context, StatusListActivity::class.java).apply {
                putExtra(EXTRA_KIND, Kind.FAVOURITES.name)
            }

        @JvmStatic
        fun newBookmarksIntent(context: Context) =
            Intent(context, StatusListActivity::class.java).apply {
                putExtra(EXTRA_KIND, Kind.BOOKMARKS.name)
            }
    }
}
