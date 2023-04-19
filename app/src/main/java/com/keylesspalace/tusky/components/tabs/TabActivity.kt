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

package com.keylesspalace.tusky.components.tabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.TabData.Action.FragmentAction
import com.keylesspalace.tusky.TabData.Action.IntentAction
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.databinding.ActivityTabsBinding
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.util.viewBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class TabActivity : BottomSheetActivity(), ActionButtonActivity, HasAndroidInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var eventHub: EventHub

    private val binding: ActivityTabsBinding by viewBinding(ActivityTabsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val tabData = intent.getParcelableExtra<TabData>(TAB_DATA)!!
        val accountLocked = intent.getBooleanExtra(ACCOUNT_LOCKED, false)

        setSupportActionBar(binding.includedToolbar.toolbar)

        val title = getString(tabData.text)

        supportActionBar?.run {
            setTitle(title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        when (tabData.action) {
            is FragmentAction -> {
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    supportFragmentManager.commit {
                        val fragment = tabData.action.fragment(tabData.arguments)
                        replace(R.id.fragmentContainer, fragment)
                    }
                }
            }

            is IntentAction -> {
                // Passing an intent action to the Tab Activity is likely an error. We can redirect
                // it to start a new activity directly, using the passed intent.
                val intent = tabData.action.intent(this@TabActivity, tabData.arguments, accountLocked)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun getActionButton(): FloatingActionButton? {
        return null
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {
        const val TAG = "TabActivity"
        private const val TAB_DATA = "tab_data"
        private const val ACCOUNT_LOCKED = "account_locked"

        @JvmStatic
        fun getIntent(context: Context, tabData: TabData, accountLocked: Boolean) =
            Intent(context, TabActivity::class.java).apply {
                putExtra(TAB_DATA, tabData)
                putExtra(ACCOUNT_LOCKED, accountLocked)
            }
    }
}
