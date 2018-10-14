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

package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.MenuItem
import com.keylesspalace.tusky.fragment.AccountPreferencesFragment
import com.keylesspalace.tusky.fragment.NotificationPreferencesFragment
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

class AccountPreferencesActivity : BaseActivity(), HasSupportFragmentInjector {

    @Inject
    lateinit var fragmentInjector: DispatchingAndroidInjector<Fragment>

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_preferences)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        if(intent.getBooleanExtra(EXTRA_SHOW_NOTIFICATIONS, false)) {
            setTitle(R.string.pref_title_edit_notification_settings)
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, NotificationPreferencesFragment.newInstance())
                    .commit()
        } else {
            setTitle(R.string.action_view_account_preferences)
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AccountPreferencesFragment.newInstance())
                    .commit()
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return fragmentInjector
    }

    companion object {

        private const val EXTRA_SHOW_NOTIFICATIONS = "SHOW_NOTIFICATIONS"

        @JvmStatic
        @JvmOverloads
        fun newIntent(context: Context, showNotificationPreferences: Boolean = false): Intent {
            val intent = Intent(context, AccountPreferencesActivity::class.java)
            intent.putExtra(EXTRA_SHOW_NOTIFICATIONS, showNotificationPreferences)
            return intent
        }
    }

}
