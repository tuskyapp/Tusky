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
import androidx.activity.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.databinding.ActivityScheduledTootBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.util.Status
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import javax.inject.Inject

class ScheduledTootActivity : BaseActivity(), ScheduledTootActionListener, Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ScheduledTootViewModel by viewModels { viewModelFactory }

    private val adapter = ScheduledTootAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityScheduledTootBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            title = getString(R.string.title_scheduled_toot)
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

        viewModel.data.observe(this) {
            adapter.submitList(it)
        }

        viewModel.networkState.observe(this) { (status) ->
            when(status) {
                Status.SUCCESS -> {
                    binding.progressBar.hide()
                    binding.swipeRefreshLayout.isRefreshing = false
                    if(viewModel.data.value?.loadedCount == 0) {
                        binding.errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_scheduled_status)
                        binding.errorMessageView.show()
                    } else {
                        binding.errorMessageView.hide()
                    }
                }
                Status.RUNNING -> {
                    binding.errorMessageView.hide()
                    if(viewModel.data.value?.loadedCount ?: 0 > 0) {
                        binding.swipeRefreshLayout.isRefreshing = true
                    } else {
                        binding.progressBar.show()
                    }
                }
                Status.FAILED -> {
                    if(viewModel.data.value?.loadedCount ?: 0 >= 0) {
                        binding.progressBar.hide()
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.errorMessageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                            refreshStatuses()
                        }
                        binding.errorMessageView.show()
                    }
                }
            }
        }
    }

    private fun refreshStatuses() {
        viewModel.reload()
    }

    override fun edit(item: ScheduledStatus) {
        val intent = ComposeActivity.startIntent(this, ComposeActivity.ComposeOptions(
                scheduledTootId = item.id,
                tootText = item.params.text,
                contentWarning = item.params.spoilerText,
                mediaAttachments = item.mediaAttachments,
                inReplyToId = item.params.inReplyToId,
                visibility = item.params.visibility,
                scheduledAt = item.scheduledAt,
                sensitive = item.params.sensitive
        ))
        startActivity(intent)
    }

    override fun delete(item: ScheduledStatus) {
        viewModel.deleteScheduledStatus(item)
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, ScheduledTootActivity::class.java)
        }
    }
}
