/* Copyright 2018 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.fragment.preference

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import tech.bigfig.roma.R
import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.di.Injectable
import tech.bigfig.roma.util.NotificationHelper
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

            val repostedPref = requirePreference("notificationFilterReblogs") as SwitchPreferenceCompat
            repostedPref.isChecked = activeAccount.notificationsReblogged
            repostedPref.onPreferenceChangeListener = this

            val favouritedPref = requirePreference("notificationFilterFavourites") as SwitchPreferenceCompat
            favouritedPref.isChecked = activeAccount.notificationsFavourited
            favouritedPref.onPreferenceChangeListener = this

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
                        NotificationHelper.enablePullNotifications(activeAccount.username)
                    } else {
                        NotificationHelper.disablePullNotifications(activeAccount.username,activeAccount.domain)
                    }
                }
                "notificationFilterMentions" -> activeAccount.notificationsMentioned = newValue as Boolean
                "notificationFilterFollows" -> activeAccount.notificationsFollowed = newValue as Boolean
                "notificationFilterReblogs" -> activeAccount.notificationsReblogged = newValue as Boolean
                "notificationFilterFavourites" -> activeAccount.notificationsFavourited = newValue as Boolean
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
