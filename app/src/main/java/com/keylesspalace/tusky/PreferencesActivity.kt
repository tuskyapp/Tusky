/* Copyright 2018 Conny Duck
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
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.fragment.app.Fragment
import android.util.Log
import android.view.MenuItem
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.fragment.preference.*
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.getNonNullString
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.toolbar_basic.*
import java.lang.IllegalArgumentException
import javax.inject.Inject
import androidx.appcompat.app.AppCompatDelegate

class PreferencesActivity : BaseActivity(), SharedPreferences.OnSharedPreferenceChangeListener, HasSupportFragmentInjector {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var fragmentInjector: DispatchingAndroidInjector<Fragment>

    private var restartActivitiesOnExit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_preferences)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val fragment: Fragment = when(intent.getIntExtra(EXTRA_PREFERENCE_TYPE, 0)) {
            GENERAL_PREFERENCES -> {
                setTitle(R.string.action_view_preferences)
                PreferencesFragment.newInstance()
            }
            ACCOUNT_PREFERENCES -> {
                setTitle(R.string.action_view_account_preferences)
                AccountPreferencesFragment.newInstance()
            }
            NOTIFICATION_PREFERENCES -> {
                setTitle(R.string.pref_title_edit_notification_settings)
                NotificationPreferencesFragment.newInstance()
            }
            TAB_FILTER_PREFERENCES -> {
                setTitle(R.string.pref_title_status_tabs)
                TabFilterPreferencesFragment.newInstance()
            }
            PROXY_PREFERENCES -> {
                setTitle(R.string.pref_title_http_proxy_settings)
                ProxyPreferencesFragment.newInstance()
            }
            else -> throw IllegalArgumentException("preferenceType not known")
        }

        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()

        restartActivitiesOnExit = intent.getBooleanExtra("restart", false)

    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
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

    private fun saveInstanceState(outState: Bundle) {
        outState.putBoolean("restart", restartActivitiesOnExit)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("restart", restartActivitiesOnExit)
        super.onSaveInstanceState(outState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "appTheme" -> {
                val theme = sharedPreferences.getNonNullString("appTheme", ThemeUtils.APP_THEME_DEFAULT)
                Log.d("activeTheme", theme)
                ThemeUtils().setAppNightMode(theme, this)
                restartActivitiesOnExit = true

                // recreate() could be used instead, but it doesn't have an animation B).
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                val savedInstanceState = Bundle()
                saveInstanceState(savedInstanceState)
                intent.putExtras(savedInstanceState)
                startActivityWithSlideInAnimation(intent)
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

                // MODE_NIGHT_FOLLOW_SYSTEM workaround part 2 :/
                when(theme){
                    ThemeUtils.THEME_SYSTEM -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                }
                //workaround end

            }
            "statusTextSize" -> {
                restartActivitiesOnExit = true
            }
            "absoluteTimeView" -> {
                restartActivitiesOnExit = true
            }
        }

        eventHub.dispatch(PreferenceChangedEvent(key))
    }


    override fun onBackPressed() {
        /* Switching themes won't actually change the theme of activities on the back stack.
         * Either the back stack activities need to all be recreated, or do the easier thing, which
         * is hijack the back button press and use it to launch a new MainActivity and clear the
         * back stack. */
        if (restartActivitiesOnExit) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivityWithSlideInAnimation(intent)
        } else {
            super.onBackPressed()
        }
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return fragmentInjector
    }

    companion object {

        const val GENERAL_PREFERENCES = 0
        const val ACCOUNT_PREFERENCES = 1
        const val NOTIFICATION_PREFERENCES = 2
        const val TAB_FILTER_PREFERENCES = 3
        const val PROXY_PREFERENCES = 4
        private const val EXTRA_PREFERENCE_TYPE = "EXTRA_PREFERENCE_TYPE"

        @JvmStatic
        fun newIntent(context: Context, preferenceType: Int): Intent {
            val intent = Intent(context, PreferencesActivity::class.java)
            intent.putExtra(EXTRA_PREFERENCE_TYPE, preferenceType)
            return intent
        }
    }

}
