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

package com.keylesspalace.tusky.fragment.preference

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.NotificationHelper
import javax.inject.Inject

class NotificationPreferencesFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener, Injectable {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.notification_preferences)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activeAccount = accountManager.activeAccount

        if (activeAccount != null) {

            val notificationPref = requirePreference("notificationsEnabled") as SwitchPreferenceCompat
            notificationPref.isChecked = activeAccount.notificationsEnabled
            notificationPref.onPreferenceChangeListener = this

            val mentionedPref = requirePreference("notificationFilterMentions") as SwitchPreferenceCompat
            mentionedPref.isChecked = activeAccount.notificationsMentioned
            mentionedPref.onPreferenceChangeListener = this

            val followedPref = requirePreference("notificationFilterFollows") as SwitchPreferenceCompat
            followedPref.isChecked = activeAccount.notificationsFollowed
            followedPref.onPreferenceChangeListener = this

            val boostedPref = requirePreference("notificationFilterReblogs") as SwitchPreferenceCompat
            boostedPref.isChecked = activeAccount.notificationsReblogged
            boostedPref.onPreferenceChangeListener = this

            val favoritedPref = requirePreference("notificationFilterFavourites") as SwitchPreferenceCompat
            favoritedPref.isChecked = activeAccount.notificationsFavorited
            favoritedPref.onPreferenceChangeListener = this

            val pollsPref = requirePreference("notificationFilterPolls") as SwitchPreferenceCompat
            pollsPref.isChecked = activeAccount.notificationsPolls
            pollsPref.onPreferenceChangeListener = this

            val soundPref = requirePreference("notificationAlertSound") as SwitchPreferenceCompat
            soundPref.isChecked = activeAccount.notificationSound
            soundPref.onPreferenceChangeListener = this

            val vibrationPref = requirePreference("notificationAlertVibrate") as SwitchPreferenceCompat
            vibrationPref.isChecked = activeAccount.notificationVibration
            vibrationPref.onPreferenceChangeListener = this

            val lightPref = requirePreference("notificationAlertLight") as SwitchPreferenceCompat
            lightPref.isChecked = activeAccount.notificationLight
            lightPref.onPreferenceChangeListener = this
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {

        val activeAccount = accountManager.activeAccount

        if (activeAccount != null) {
            when (preference.key) {
                "notificationsEnabled" -> {
                    activeAccount.notificationsEnabled = newValue as Boolean
                    if (NotificationHelper.areNotificationsEnabled(preference.context, accountManager)) {
                        NotificationHelper.enablePullNotifications()
                    } else {
                        NotificationHelper.disablePullNotifications()
                    }
                }
                "notificationFilterMentions" -> activeAccount.notificationsMentioned = newValue as Boolean
                "notificationFilterFollows" -> activeAccount.notificationsFollowed = newValue as Boolean
                "notificationFilterReblogs" -> activeAccount.notificationsReblogged = newValue as Boolean
                "notificationFilterFavourites" -> activeAccount.notificationsFavorited = newValue as Boolean
                "notificationFilterPolls" -> activeAccount.notificationsPolls = newValue as Boolean
                "notificationAlertSound" -> activeAccount.notificationSound = newValue as Boolean
                "notificationAlertVibrate" -> activeAccount.notificationVibration = newValue as Boolean
                "notificationAlertLight" -> activeAccount.notificationLight = newValue as Boolean
            }
            accountManager.saveAccount(activeAccount)
            return true
        }

        return false
    }

    companion object {
        fun newInstance(): NotificationPreferencesFragment {
            return NotificationPreferencesFragment()
        }
    }

}
