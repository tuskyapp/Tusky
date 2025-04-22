/* Copyright 2024 Tusky Contributors
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityNotificationPolicyBinding
import com.keylesspalace.tusky.usecase.NotificationPolicyState
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationPoliciesActivity : BaseActivity() {

    private val viewModel: NotificationPoliciesViewModel by viewModels()

    private val binding by viewBinding(ActivityNotificationPolicyBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.notification_policies_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.progressBar.visible(state is NotificationPolicyState.Loading)
                binding.preferenceFragment.visible(state is NotificationPolicyState.Loaded)
                binding.messageView.visible(state !is NotificationPolicyState.Loading && state !is NotificationPolicyState.Loaded)
                when (state) {
                    is NotificationPolicyState.Loading -> {}

                    is NotificationPolicyState.Error ->
                        binding.messageView.setup(state.throwable) { viewModel.loadPolicy() }

                    is NotificationPolicyState.Loaded -> {}

                    NotificationPolicyState.Unsupported ->
                        binding.messageView.setup(R.drawable.errorphant_error, R.string.notification_policies_not_supported) { viewModel.loadPolicy() }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                Snackbar.make(
                    binding.root,
                    error.getErrorString(this@NotificationPoliciesActivity),
                    LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, NotificationPoliciesActivity::class.java)
    }
}
