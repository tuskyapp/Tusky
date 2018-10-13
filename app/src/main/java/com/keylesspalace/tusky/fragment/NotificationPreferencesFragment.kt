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

package com.keylesspalace.tusky.fragment

import android.os.Bundle
import android.support.v14.preference.SwitchPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.View
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
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

            val notificationPref = findPreference("notificationsEnabled") as SwitchPreference
            notificationPref.isChecked = activeAccount.notificationsEnabled
            notificationPref.onPreferenceChangeListener = this

            val mentionedPref = findPreference("notificationFilterMentions") as SwitchPreference
            mentionedPref.isChecked = activeAccount.notificationsMentioned
            mentionedPref.onPreferenceChangeListener = this

            val followedPref = findPreference("notificationFilterFollows") as SwitchPreference
            followedPref.isChecked = activeAccount.notificationsFollowed
            followedPref.onPreferenceChangeListener = this

            val boostedPref = findPreference("notificationFilterReblogs") as SwitchPreference
            boostedPref.isChecked = activeAccount.notificationsReblogged
            boostedPref.onPreferenceChangeListener = this

            val favoritedPref = findPreference("notificationFilterFavourites") as SwitchPreference
            favoritedPref.isChecked = activeAccount.notificationsFavorited
            favoritedPref.onPreferenceChangeListener = this

            val soundPref = findPreference("notificationAlertSound") as SwitchPreference
            soundPref.isChecked = activeAccount.notificationSound
            soundPref.onPreferenceChangeListener = this

            val vibrationPref = findPreference("notificationAlertVibrate") as SwitchPreference
            vibrationPref.isChecked = activeAccount.notificationVibration
            vibrationPref.onPreferenceChangeListener = this

            val lightPref = findPreference("notificationAlertLight") as SwitchPreference
            lightPref.isChecked = activeAccount.notificationLight
            lightPref.onPreferenceChangeListener = this
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {

        val activeAccount = accountManager.activeAccount

        if (activeAccount != null) {
            when (preference.key) {
                "notificationsEnabled" -> activeAccount.notificationsEnabled = newValue as Boolean
                "notificationFilterMentions" -> activeAccount.notificationsMentioned = newValue as Boolean
                "notificationFilterFollows" -> activeAccount.notificationsFollowed = newValue as Boolean
                "notificationFilterReblogs" -> activeAccount.notificationsReblogged = newValue as Boolean
                "notificationFilterFavourites" -> activeAccount.notificationsFavorited = newValue as Boolean
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
