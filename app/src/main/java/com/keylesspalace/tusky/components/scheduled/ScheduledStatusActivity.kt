/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.components.scheduled

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import autodispose2.androidx.lifecycle.autoDispose
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusScheduledEvent
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.databinding.ActivityScheduledStatusBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ScheduledStatusActivity :
    BaseActivity(),
    ScheduledStatusActionListener,
    MenuProvider,
    Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var eventHub: EventHub

    private val viewModel: ScheduledStatusViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(ActivityScheduledStatusBinding::inflate)

    private val adapter = ScheduledStatusAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        addMenuProvider(this)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            title = getString(R.string.title_scheduled_posts)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshStatuses)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        binding.scheduledTootList.setHasFixedSize(true)
        binding.scheduledTootList.layoutManager = LinearLayoutManager(this)
        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        binding.scheduledTootList.addItemDecoration(divider)
        binding.scheduledTootList.adapter = adapter

        lifecycleScope.launch {
            viewModel.data.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Error) {
                binding.progressBar.hide()
                binding.errorMessageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                    refreshStatuses()
                }
                binding.errorMessageView.show()
            }
            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            if (loadState.refresh is LoadState.NotLoading) {
                binding.progressBar.hide()
                if (adapter.itemCount == 0) {
                    binding.errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_scheduled_posts)
                    binding.errorMessageView.show()
                } else {
                    binding.errorMessageView.hide()
                }
            }
        }

        eventHub.events
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(this)
            .subscribe { event ->
                if (event is StatusScheduledEvent) {
                    adapter.refresh()
                }
            }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_announcements, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@ScheduledStatusActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.includedToolbar.toolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshStatuses()
                true
            }
            else -> false
        }
    }

    private fun refreshStatuses() {
        adapter.refresh()
    }

    override fun edit(item: ScheduledStatus) {
        val intent = ComposeActivity.startIntent(
            this,
            ComposeActivity.ComposeOptions(
                scheduledTootId = item.id,
                content = item.params.text,
                contentWarning = item.params.spoilerText,
                mediaAttachments = item.mediaAttachments,
                inReplyToId = item.params.inReplyToId,
                visibility = item.params.visibility,
                scheduledAt = item.scheduledAt,
                sensitive = item.params.sensitive,
                kind = ComposeActivity.ComposeKind.EDIT_SCHEDULED
            )
        )
        startActivity(intent)
    }

    override fun delete(item: ScheduledStatus) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_scheduled_post_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteScheduledStatus(item)
            }
            .show()
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, ScheduledStatusActivity::class.java)
    }
}
