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

package com.keylesspalace.tusky.components.preference

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.databinding.ActivityPreferencesBinding
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.getNonNullString
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class PreferencesActivity :
    BaseActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    HasAndroidInjector {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    private var restartActivitiesOnExit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val fragmentTag = "preference_fragment_$EXTRA_PREFERENCE_TYPE"

        val fragment: Fragment = supportFragmentManager.findFragmentByTag(fragmentTag)
            ?: when (intent.getIntExtra(EXTRA_PREFERENCE_TYPE, 0)) {
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

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }

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
                ThemeUtils.setAppNightMode(theme)

                restartActivitiesOnExit = true
                this.restartCurrentActivity()
            }
            "statusTextSize", "absoluteTimeView", "showBotOverlay", "animateGifAvatars", "useBlurhash",
            "showCardsInTimelines", "confirmReblogs", "confirmFavourites",
            "enableSwipeForTabs", "mainNavPosition", PrefKeys.HIDE_TOP_TOOLBAR -> {
                restartActivitiesOnExit = true
            }
            "language" -> {
                restartActivitiesOnExit = true
                this.restartCurrentActivity()
            }
        }

        eventHub.dispatch(PreferenceChangedEvent(key))
    }

    private fun restartCurrentActivity() {
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        val savedInstanceState = Bundle()
        saveInstanceState(savedInstanceState)
        intent.putExtras(savedInstanceState)
        startActivityWithSlideInAnimation(intent)
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
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

    override fun androidInjector() = androidInjector

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
