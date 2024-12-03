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

package com.keylesspalace.tusky.components.preference.notificationpolicies

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.usecase.NotificationPolicyState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationPoliciesFragment : PreferenceFragmentCompat() {

    val viewModel: NotificationPoliciesViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            preferenceCategory(title = R.string.notification_policies_filter_out) { category ->
                category.isIconSpaceReserved = false

                notificationPolicyPreference {
                    setTitle(R.string.notification_policies_filter_dont_follow_title)
                    setSummary(R.string.notification_policies_filter_dont_follow_description)
                    key = KEY_NOT_FOLLOWING
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePolicy(forNotFollowing = newValue as String)
                        true
                    }
                }

                notificationPolicyPreference {
                    setTitle(R.string.notification_policies_filter_not_following_title)
                    setSummary(R.string.notification_policies_filter_not_following_description)
                    key = KEY_NOT_FOLLOWERS
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePolicy(forNotFollowers = newValue as String)
                        true
                    }
                }

                notificationPolicyPreference {
                    setTitle(R.string.unknown_notification_filter_new_accounts_title)
                    setSummary(R.string.unknown_notification_filter_new_accounts_description)
                    key = KEY_NEW_ACCOUNTS
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePolicy(forNewAccounts = newValue as String)
                        true
                    }
                }

                notificationPolicyPreference {
                    setTitle(R.string.unknown_notification_filter_unsolicited_private_mentions_title)
                    setSummary(R.string.unknown_notification_filter_unsolicited_private_mentions_description)
                    key = KEY_PRIVATE_MENTIONS
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePolicy(forPrivateMentions = newValue as String)
                        true
                    }
                }

                notificationPolicyPreference {
                    setTitle(R.string.unknown_notification_filter_moderated_accounts)
                    setSummary(R.string.unknown_notification_filter_moderated_accounts_description)
                    key = KEY_LIMITED_ACCOUNTS
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePolicy(forLimitedAccounts = newValue as String)
                        true
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (state is NotificationPolicyState.Loaded) {
                    findPreference<NotificationPolicyPreference>(KEY_NOT_FOLLOWING)?.value = state.policy.forNotFollowing.name.lowercase()
                    findPreference<NotificationPolicyPreference>(KEY_NOT_FOLLOWERS)?.value = state.policy.forNotFollowers.name.lowercase()
                    findPreference<NotificationPolicyPreference>(KEY_NEW_ACCOUNTS)?.value = state.policy.forNewAccounts.name.lowercase()
                    findPreference<NotificationPolicyPreference>(KEY_PRIVATE_MENTIONS)?.value = state.policy.forPrivateMentions.name.lowercase()
                    findPreference<NotificationPolicyPreference>(KEY_LIMITED_ACCOUNTS)?.value = state.policy.forLimitedAccounts.name.lowercase()
                }
            }
        }
    }

    companion object {
        fun newInstance(): NotificationPoliciesFragment {
            return NotificationPoliciesFragment()
        }

        private const val KEY_NOT_FOLLOWING = "NOT_FOLLOWING"
        private const val KEY_NOT_FOLLOWERS = "NOT_FOLLOWERS"
        private const val KEY_NEW_ACCOUNTS = "NEW_ACCOUNTS"
        private const val KEY_PRIVATE_MENTIONS = "PRIVATE MENTIONS"
        private const val KEY_LIMITED_ACCOUNTS = "LIMITED_ACCOUNTS"
    }
}
