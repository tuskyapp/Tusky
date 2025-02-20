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
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationPreferencesFragment : BasePreferencesFragment() {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var notificationService: NotificationService

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activeAccount = accountManager.activeAccount ?: return
        makePreferenceScreen {
            switchPreference {
                setTitle(R.string.pref_title_notifications_enabled)
                key = PrefKeys.NOTIFICATIONS_ENABLED
                isIconSpaceReserved = false
                isChecked = activeAccount.notificationsEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    updateAccount { copy(notificationsEnabled = newValue as Boolean) }
                    if (notificationService.areNotificationsEnabledBySystem()) {
                        notificationService.enablePullNotifications()
                    } else {
                        notificationService.disablePullNotifications()
                    }
                    true
                }
            }

            preferenceCategory(R.string.pref_title_notification_filters) { category ->
                category.dependency = PrefKeys.NOTIFICATIONS_ENABLED
                category.isIconSpaceReserved = false

                switchPreference {
                    setTitle(R.string.notification_follow_name)
                    setSummary(R.string.notification_follow_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowed
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsFollowed = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_follow_request_name)
                    setSummary(R.string.notification_follow_request_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowRequested
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsFollowRequested = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_boost_name)
                    setSummary(R.string.notification_boost_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReblogged
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsReblogged = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_favourite_name)
                    setSummary(R.string.notification_favourite_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFavorited
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsFavorited = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_poll_name)
                    setSummary(R.string.notification_poll_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsPolls
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsPolls = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_subscription_name)
                    setSummary(R.string.notification_subscription_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSubscriptions
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsSubscriptions = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_update_name)
                    setSummary(R.string.notification_update_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsUpdates
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsUpdates = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_channel_admin)
                    setSummary(R.string.notification_channel_admin_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsAdmin
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsAdmin = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.notification_channel_other)
                    setSummary(R.string.notification_channel_other_description)
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsOther
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationsOther = newValue as Boolean) }
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
                        updateAccount { copy(notificationSound = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_vibrate)
                    key = PrefKeys.NOTIFICATION_ALERT_VIBRATE
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationVibration
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationVibration = newValue as Boolean) }
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_light)
                    key = PrefKeys.NOTIFICATION_ALERT_LIGHT
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationLight
                    setOnPreferenceChangeListener { _, newValue ->
                        updateAccount { copy(notificationLight = newValue as Boolean) }
                        true
                    }
                }
            }
        }
    }

    private fun updateAccount(changer: AccountEntity.() -> AccountEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            accountManager.activeAccount?.let { account ->
                accountManager.updateAccount(account, changer)
            }
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
