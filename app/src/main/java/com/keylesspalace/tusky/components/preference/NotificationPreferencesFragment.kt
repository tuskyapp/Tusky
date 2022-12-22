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

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import javax.inject.Inject

class NotificationPreferencesFragment : PreferenceFragmentCompat(), Injectable {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activeAccount = accountManager.activeAccount ?: return
        val context = requireContext()
        makePreferenceScreen {
            switchPreference {
                setTitle(R.string.pref_title_notifications_enabled)
                key = PrefKeys.NOTIFICATIONS_ENABLED
                isIconSpaceReserved = false
                isChecked = activeAccount.notificationsEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    updateAccount { it.notificationsEnabled = newValue as Boolean }
                    if (NotificationHelper.areNotificationsEnabled(context, accountManager)) {
                        NotificationHelper.enablePullNotifications(context)
                    } else {
                        NotificationHelper.disablePullNotifications(context)
                    }
                    true
                }
            }

            preferenceCategory(R.string.pref_title_notification_filters) { category ->
                category.dependency = PrefKeys.NOTIFICATIONS_ENABLED
                category.isIconSpaceReserved = false

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follows)
                    key = PrefKeys.NOTIFICATIONS_FILTER_FOLLOWS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowed
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsFollowed = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follow_requests)
                    key = PrefKeys.NOTIFICATION_FILTER_FOLLOW_REQUESTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowRequested
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsFollowRequested = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reblogs)
                    key = PrefKeys.NOTIFICATION_FILTER_REBLOGS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReblogged
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsReblogged = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_favourites)
                    key = PrefKeys.NOTIFICATION_FILTER_FAVS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFavorited
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsFavorited = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_poll)
                    key = PrefKeys.NOTIFICATION_FILTER_POLLS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsPolls
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsPolls = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_subscriptions)
                    key = PrefKeys.NOTIFICATION_FILTER_SUBSCRIPTIONS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSubscriptions
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsSubscriptions = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_sign_ups)
                    key = PrefKeys.NOTIFICATION_FILTER_SIGN_UPS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSignUps
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsSignUps = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_updates)
                    key = PrefKeys.NOTIFICATION_FILTER_UPDATES
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsUpdates
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsUpdates = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reports)
                    key = PrefKeys.NOTIFICATION_FILTER_REPORTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReports
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationsReports = newValue as Boolean }
                        true
                    }
                }
            }

            preferenceCategory(R.string.pref_title_notification_alerts) { category ->
                category.dependency = PrefKeys.NOTIFICATIONS_ENABLED
                category.isIconSpaceReserved = false

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_sound)
                    key = PrefKeys.NOTIFICATION_ALERT_SOUND
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationSound
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationSound = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_vibrate)
                    key = PrefKeys.NOTIFICATION_ALERT_VIBRATE
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationVibration
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationVibration = newValue as Boolean }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_light)
                    key = PrefKeys.NOTIFICATION_ALERT_LIGHT
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationLight
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { it.notificationLight = newValue as Boolean }
                        true
                    }
                }
            }
        }
    }

    private inline fun updateAccount(changer: (AccountEntity) -> Unit) {
        accountManager.activeAccount?.let { account ->
            changer(account)
            accountManager.saveAccount(account)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.pref_title_edit_notification_settings)
    }

    companion object {
        fun newInstance(): NotificationPreferencesFragment {
            return NotificationPreferencesFragment()
        }
    }
}
