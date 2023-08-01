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

package com.keylesspalace.tusky.components.trending

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityTrendingBinding
import com.keylesspalace.tusky.util.reduceSwipeSensitivity
import com.keylesspalace.tusky.util.viewBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class TrendingActivity : BaseActivity(), HasAndroidInjector, MenuProvider {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    private val binding: ActivityTrendingBinding by viewBinding(ActivityTrendingBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        supportActionBar?.run {
            setTitle(R.string.title_public_trending)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val adapter = TrendingFragmentAdapter(this)
        binding.pager.adapter = adapter
        binding.pager.reduceSwipeSensitivity()

        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.text = adapter.title(position)
        }.attach()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                @SuppressLint("SyntheticAccessor")
                override fun handleOnBackPressed() {
                    if (binding.pager.currentItem != 0) binding.pager.currentItem = 0 else finish()
                }
            }
        )
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_trending, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return super.onOptionsItemSelected(menuItem)
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {
        fun getIntent(context: Context) = Intent(context, TrendingActivity::class.java)
    }
}

class TrendingFragmentAdapter(val activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TrendingTagsFragment.newInstance()
            1 -> TrendingLinksFragment.newInstance()
            else -> throw IllegalStateException()
        }
    }

    fun title(position: Int): CharSequence {
        return when (position) {
            0 -> activity.getString(R.string.title_tab_public_trending_hashtags)
            1 -> activity.getString(R.string.title_tab_public_trending_links)
            else -> throw IllegalStateException()
        }
    }
}
