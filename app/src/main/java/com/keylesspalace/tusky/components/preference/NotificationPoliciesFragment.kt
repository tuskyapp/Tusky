/* Copyright 2024 Tusky contributors
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
import com.google.android.material.materialswitch.MaterialSwitch
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.systemnotifications.NotificationHelper
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationPoliciesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activeAccount = accountManager.activeAccount ?: return

        val ms= MaterialSwitch(requireContext())


        val context = requireContext()
        makePreferenceScreen {
            preferenceCategory(title = R.string.notification_policies_filter_out) {
                switchPreference {
                    setTitle(R.string.notification_policies_filter_dont_follow_title)
                    setSummary(R.string.notification_policies_filter_dont_follow_description)
                    key = PrefKeys.NOTIFICATIONS_ENABLED
                    isChecked = activeAccount.notificationsEnabled
                    setOnPreferenceChangeListener { _, newValue ->

                        true
                    }
                }
                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follows)
                    key = PrefKeys.NOTIFICATIONS_FILTER_FOLLOWS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowed
                    setOnPreferenceChangeListener { _, newValue ->
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follow_requests)
                    key = PrefKeys.NOTIFICATION_FILTER_FOLLOW_REQUESTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowRequested
                    setOnPreferenceChangeListener { _, newValue ->
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reblogs)
                    key = PrefKeys.NOTIFICATION_FILTER_REBLOGS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReblogged
                    setOnPreferenceChangeListener { _, newValue ->
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
        fun newInstance(): NotificationPoliciesFragment {
            return NotificationPoliciesFragment()
        }
    }
}
