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
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.databinding.ActivityPreferencesBinding
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.PrefKeys.APP_THEME
import com.keylesspalace.tusky.util.getNonNullString
import com.keylesspalace.tusky.util.setAppNightMode
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import dagger.hilt.android.AndroidEntryPoint
import de.c1710.filemojicompat_ui.helpers.EMOJI_PREFERENCE
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreferencesActivity :
    BaseActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Inject
    lateinit var eventHub: EventHub

    private val restartActivitiesOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            /* Switching themes won't actually change the theme of activities on the back stack.
             * Either the back stack activities need to all be recreated, or do the easier thing, which
             * is hijack the back button press and use it to launch a new MainActivity and clear the
             * back stack. */
            val intent = Intent(this@PreferencesActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivityWithSlideInAnimation(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Workaround for edge-to-edge mode not working when an activity is recreated
        // https://stackoverflow.com/questions/79319740/edge-to-edge-doesnt-work-when-activity-recreated-or-appcompatdelegate-setdefaul
        if (savedInstanceState != null && Build.VERSION.SDK_INT >= 35) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        val binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val preferenceType = intent.getIntExtra(EXTRA_PREFERENCE_TYPE, 0)

        val fragmentTag = "preference_fragment_$preferenceType"

        val fragment: Fragment = supportFragmentManager.findFragmentByTag(fragmentTag)
            ?: when (preferenceType) {
                GENERAL_PREFERENCES -> PreferencesFragment.newInstance()
                ACCOUNT_PREFERENCES -> AccountPreferencesFragment.newInstance()
                NOTIFICATION_PREFERENCES -> NotificationPreferencesFragment.newInstance()
                else -> throw IllegalArgumentException("preferenceType not known")
            }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }

        onBackPressedDispatcher.addCallback(this, restartActivitiesOnBackPressedCallback)
        restartActivitiesOnBackPressedCallback.isEnabled = savedInstanceState?.getBoolean(EXTRA_RESTART_ON_BACK, false) == true
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        supportFragmentManager.commit {
            setCustomAnimations(
                R.anim.activity_open_enter,
                R.anim.activity_open_exit,
                R.anim.activity_close_enter,
                R.anim.activity_close_exit
            )
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_RESTART_ON_BACK, restartActivitiesOnBackPressedCallback.isEnabled)
        super.onSaveInstanceState(outState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        key ?: return
        when (key) {
            APP_THEME -> {
                val theme = sharedPreferences.getNonNullString(APP_THEME, AppTheme.DEFAULT.value)
                Log.d("activeTheme", theme)
                setAppNightMode(theme)

                restartActivitiesOnBackPressedCallback.isEnabled = true
                this.recreate()
            }
            PrefKeys.UI_TEXT_SCALE_RATIO -> {
                restartActivitiesOnBackPressedCallback.isEnabled = true
                this.recreate()
            }
            PrefKeys.STATUS_TEXT_SIZE, PrefKeys.ABSOLUTE_TIME_VIEW, PrefKeys.SHOW_BOT_OVERLAY, PrefKeys.ANIMATE_GIF_AVATARS, PrefKeys.USE_BLURHASH,
            PrefKeys.SHOW_SELF_USERNAME, PrefKeys.SHOW_CARDS_IN_TIMELINES, EMOJI_PREFERENCE, PrefKeys.ENABLE_SWIPE_FOR_TABS,
            PrefKeys.MAIN_NAV_POSITION, PrefKeys.HIDE_TOP_TOOLBAR, PrefKeys.SHOW_STATS_INLINE -> {
                restartActivitiesOnBackPressedCallback.isEnabled = true
            }
        }
        lifecycleScope.launch {
            eventHub.dispatch(PreferenceChangedEvent(key))
        }
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "PreferencesActivity"
        const val GENERAL_PREFERENCES = 0
        const val ACCOUNT_PREFERENCES = 1
        const val NOTIFICATION_PREFERENCES = 2
        private const val EXTRA_PREFERENCE_TYPE = "EXTRA_PREFERENCE_TYPE"
        private const val EXTRA_RESTART_ON_BACK = "restart"

        @JvmStatic
        fun newIntent(context: Context, preferenceType: Int): Intent {
            val intent = Intent(context, PreferencesActivity::class.java)
            intent.putExtra(EXTRA_PREFERENCE_TYPE, preferenceType)
            return intent
        }
    }
}
