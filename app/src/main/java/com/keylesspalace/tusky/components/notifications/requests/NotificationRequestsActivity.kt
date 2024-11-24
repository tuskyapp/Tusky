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

package com.keylesspalace.tusky.components.notifications.requests

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.notifications.requests.details.NotificationRequestDetailsActivity
import com.keylesspalace.tusky.components.preference.notificationpolicies.NotificationPoliciesActivity
import com.keylesspalace.tusky.databinding.ActivityNotificationRequestsBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.NotificationRequest
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlin.String
import kotlin.getValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationRequestsActivity : BaseActivity(), MenuProvider {

    private val viewModel: NotificationRequestsViewModel by viewModels()

    private val binding by viewBinding(ActivityNotificationRequestsBinding::inflate)

    private val notificationRequestDetails = registerForActivityResult(NotificationRequestDetailsResultContract()) { id ->
        if (id != null) {
            viewModel.removeNotificationRequest(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        addMenuProvider(this)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.filtered_notifications_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setupAdapter().let { adapter ->
            setupRecyclerView(adapter)

            lifecycleScope.launch {
                viewModel.pager.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                Snackbar.make(
                    binding.root,
                    error.getErrorString(this@NotificationRequestsActivity),
                    LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRecyclerView(adapter: NotificationRequestsAdapter) {
        binding.notificationRequestsView.adapter = adapter
        binding.notificationRequestsView.setHasFixedSize(true)
        binding.notificationRequestsView.layoutManager = LinearLayoutManager(this)
        binding.notificationRequestsView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
    }

    private fun setupAdapter(): NotificationRequestsAdapter {
        return NotificationRequestsAdapter(
            onAcceptRequest = viewModel::acceptNotificationRequest,
            onDismissRequest = viewModel::dismissNotificationRequest,
            onOpenDetails = ::onOpenRequestDetails,
            animateAvatar = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        ).apply {
            addLoadStateListener { loadState ->
                binding.notificationRequestsProgressBar.visible(
                    loadState.refresh == LoadState.Loading && itemCount == 0
                )

                if (loadState.refresh is LoadState.Error) {
                    binding.notificationRequestsView.hide()
                    binding.notificationRequestsMessageView.show()
                    val errorState = loadState.refresh as LoadState.Error
                    binding.notificationRequestsMessageView.setup(errorState.error) { retry() }
                    Log.w(TAG, "error loading notification requests", errorState.error)
                } else {
                    binding.notificationRequestsView.show()
                    binding.notificationRequestsMessageView.hide()
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_notification_requests, menu)
        menu.findItem(R.id.open_settings)?.apply {
            icon = IconicsDrawable(this@NotificationRequestsActivity, GoogleMaterial.Icon.gmd_settings).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.includedToolbar.toolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.open_settings -> {
                val intent = NotificationPoliciesActivity.newIntent(this)
                startActivityWithSlideInAnimation(intent)
                true
            }
            else -> false
        }
    }

    private fun onOpenRequestDetails(reqeuest: NotificationRequest) {
        notificationRequestDetails.launch(
            NotificationRequestDetailsResultContractInput(
                notificationRequestId = reqeuest.id,
                accountId = reqeuest.account.id,
                accountName = reqeuest.account.name,
                accountEmojis = reqeuest.account.emojis
            )
        )
    }

    class NotificationRequestDetailsResultContractInput(
        val notificationRequestId: String,
        val accountId: String,
        val accountName: String,
        val accountEmojis: List<Emoji>
    )

    class NotificationRequestDetailsResultContract : ActivityResultContract<NotificationRequestDetailsResultContractInput, String?>() {
        override fun createIntent(context: Context, input: NotificationRequestDetailsResultContractInput): Intent {
            return NotificationRequestDetailsActivity.newIntent(
                notificationRequestId = input.notificationRequestId,
                accountId = input.accountId,
                accountName = input.accountName,
                accountEmojis = input.accountEmojis,
                context = context
            )
        }

        override fun parseResult(resultCode: Int, intent: Intent?): String? {
            return intent?.getStringExtra(NotificationRequestDetailsActivity.EXTRA_NOTIFICATION_REQUEST_ID)
        }
    }

    companion object {
        private const val TAG = "NotificationRequestsActivity"
        fun newIntent(context: Context) = Intent(context, NotificationRequestsActivity::class.java)
    }
}
