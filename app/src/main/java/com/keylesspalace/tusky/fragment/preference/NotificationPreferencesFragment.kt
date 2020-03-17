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
            for (pair in mapOf(
                    "notificationsEnabled" to activeAccount.notificationsEnabled,
                    "notificationFilterMentions" to activeAccount.notificationsMentioned,
                    "notificationFilterFollows" to activeAccount.notificationsFollowed,
                    "notificationFilterFollowRequests" to activeAccount.notificationsFollowRequested,
                    "notificationFilterReblogs" to activeAccount.notificationsReblogged,
                    "notificationFilterFavourites" to activeAccount.notificationsFavorited,
                    "notificationFilterPolls" to activeAccount.notificationsPolls,
                    "notificationAlertSound" to activeAccount.notificationSound,
                    "notificationAlertVibrate" to activeAccount.notificationVibration,
                    "notificationAlertLight" to activeAccount.notificationLight
            )) {
                (requirePreference(pair.key) as SwitchPreferenceCompat).apply {
                    isChecked = pair.value
                    onPreferenceChangeListener = this@NotificationPreferencesFragment
                }
            }
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
                "notificationFilterFollowRequests" -> activeAccount.notificationsFollowRequested = newValue as Boolean
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
