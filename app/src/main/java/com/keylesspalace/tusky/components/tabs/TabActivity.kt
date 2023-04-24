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
import com.keylesspalace.tusky.ScreenData
import com.keylesspalace.tusky.TabScreenData
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.createScreenDataFromId
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

        val screenId = intent.getStringExtra(SCREEN_ID)!!
        val screenArgs = intent.getStringArrayExtra(SCREEN_ARGS)!!
        val screenData = createScreenDataFromId(screenId, screenArgs.asList())
        val accountLocked = intent.getBooleanExtra(ACCOUNT_LOCKED, false)

        setSupportActionBar(binding.includedToolbar.toolbar)

        val title = getString(screenData.text)

        supportActionBar?.run {
            setTitle(title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        when (screenData) {
            is TabScreenData -> {
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    supportFragmentManager.commit {
                        val fragment = screenData.fragmentAction(screenData.arguments)
                        replace(R.id.fragmentContainer, fragment)
                    }
                }
            }

            is ScreenData -> {
                // Passing an intent action to the Tab Activity is likely an error. We can redirect
                // it to start a new activity directly, using the passed intent.
                val intent = screenData.intentAction(this@TabActivity, screenData.arguments, accountLocked)
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
        private const val SCREEN_ID = "screen_id"
        private const val SCREEN_ARGS = "screen_args"
        private const val ACCOUNT_LOCKED = "account_locked"

        @JvmStatic
        fun getIntent(context: Context, screenId: String, screenArguments: List<String>, accountLocked: Boolean) =
            Intent(context, TabActivity::class.java).apply {
                putExtra(SCREEN_ID, screenId)
                putExtra(SCREEN_ARGS, screenArguments.toTypedArray())
                putExtra(ACCOUNT_LOCKED, accountLocked)
            }
    }
}
