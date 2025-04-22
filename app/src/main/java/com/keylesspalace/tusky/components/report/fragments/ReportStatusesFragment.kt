/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.report.Screen
import com.keylesspalace.tusky.components.report.adapter.AdapterHandler
import com.keylesspalace.tusky.components.report.adapter.StatusesAdapter
import com.keylesspalace.tusky.databinding.FragmentReportStatusesBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReportStatusesFragment :
    Fragment(R.layout.fragment_report_statuses),
    OnRefreshListener,
    MenuProvider,
    AdapterHandler {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: ReportViewModel by activityViewModels()

    private val binding by viewBinding(FragmentReportStatusesBinding::bind)

    private var adapter: StatusesAdapter? = null

    private var snackbarErrorRetry: Snackbar? = null

    override fun showMedia(v: View?, status: StatusViewData.Concrete, idx: Int) {
        when (status.attachments[idx].type) {
            Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                val attachments = AttachmentViewData.list(status)
                val intent = ViewMediaActivity.newIntent(context, attachments, idx)
                if (v != null) {
                    val url = status.attachments[idx].url
                    ViewCompat.setTransitionName(v, url)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        v,
                        url
                    )
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }

            Attachment.Type.UNKNOWN -> {
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        handleClicks()
        initStatusesView()
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        snackbarErrorRetry = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_report_statuses, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        R.id.action_refresh -> {
            binding.swipeRefreshLayout.isRefreshing = true
            onRefresh()
            true
        }

        else -> false
    }

    override fun onRefresh() {
        snackbarErrorRetry?.dismiss()
        snackbarErrorRetry = null
        adapter?.refresh()
    }

    private fun initStatusesView() {
        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = false,
            mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled != false,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = false,
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        val adapter = StatusesAdapter(statusDisplayOptions, viewModel.statusViewState, this)
        this.adapter = adapter

        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusesFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Error ||
                loadState.append is LoadState.Error ||
                loadState.prepend is LoadState.Error
            ) {
                showError(adapter)
            }

            binding.progressBarBottom.visible(loadState.append == LoadState.Loading)
            binding.progressBarTop.visible(loadState.prepend == LoadState.Loading)
            binding.progressBarLoading.visible(
                loadState.refresh == LoadState.Loading && !binding.swipeRefreshLayout.isRefreshing
            )

            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showError(adapter: StatusesAdapter) {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry =
                Snackbar.make(binding.swipeRefreshLayout, R.string.failed_fetch_posts, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_retry) {
                        adapter.retry()
                    }.also {
                        it.show()
                    }
        }
    }

    private fun handleClicks() {
        binding.buttonCancel.setOnClickListener {
            viewModel.navigateTo(Screen.Back)
        }

        binding.buttonContinue.setOnClickListener {
            viewModel.navigateTo(Screen.Note)
        }
    }

    override fun setStatusChecked(status: Status, isChecked: Boolean) {
        viewModel.setStatusChecked(status, isChecked)
    }

    override fun isStatusChecked(id: String): Boolean = viewModel.isStatusChecked(id)

    override fun onViewAccount(id: String) = startActivity(
        AccountActivity.getIntent(requireContext(), id)
    )

    override fun onViewTag(tag: String) = startActivity(
        StatusListActivity.newHashtagIntent(requireContext(), tag)
    )

    override fun onViewUrl(url: String) = viewModel.checkClickedUrl(url)

    companion object {
        fun newInstance() = ReportStatusesFragment()
    }
}
