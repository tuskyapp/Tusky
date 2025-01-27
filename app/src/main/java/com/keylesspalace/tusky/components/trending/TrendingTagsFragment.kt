/* Copyright 2023 Tusky Contributors
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

package com.keylesspalace.tusky.components.trending

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.components.trending.viewmodel.TrendingTagsViewModel
import com.keylesspalace.tusky.databinding.FragmentTrendingTagsBinding
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.TrendingViewData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrendingTagsFragment :
    Fragment(R.layout.fragment_trending_tags),
    OnRefreshListener,
    ReselectableFragment,
    RefreshableFragment {

    private val viewModel: TrendingTagsViewModel by viewModels()

    private val binding by viewBinding(FragmentTrendingTagsBinding::bind)

    private var adapter: TrendingTagsAdapter? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount =
            requireContext().resources.getInteger(R.integer.trending_column_count)
        adapter?.let {
            setupLayoutManager(it, columnCount)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TrendingTagsAdapter(::onViewTag)
        this.adapter = adapter
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.recyclerView.ensureBottomPadding()
        setupRecyclerView(adapter)

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val firstPos = (binding.recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                if (firstPos == 0 && positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            binding.recyclerView.scrollBy(
                                0,
                                Utils.dpToPx(requireContext(), -30)
                            )
                        }
                    }
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { trendingState ->
                processViewState(adapter, trendingState)
            }
        }

        (requireActivity() as? ActionButtonActivity)?.actionButton?.visibility = View.GONE
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        super.onDestroyView()
    }

    private fun setupLayoutManager(adapter: TrendingTagsAdapter, columnCount: Int) {
        binding.recyclerView.layoutManager = GridLayoutManager(context, columnCount).apply {
            spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        TrendingTagsAdapter.VIEW_TYPE_HEADER -> columnCount
                        TrendingTagsAdapter.VIEW_TYPE_TAG -> 1
                        else -> -1
                    }
                }
            }
        }
    }

    private fun setupRecyclerView(adapter: TrendingTagsAdapter) {
        val columnCount =
            requireContext().resources.getInteger(R.integer.trending_column_count)
        setupLayoutManager(adapter, columnCount)

        binding.recyclerView.setHasFixedSize(true)

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    override fun onRefresh() {
        viewModel.invalidate(true)
    }

    fun onViewTag(tag: String) {
        requireActivity().startActivityWithSlideInAnimation(
            StatusListActivity.newHashtagIntent(requireContext(), tag)
        )
    }

    private fun processViewState(
        adapter: TrendingTagsAdapter,
        uiState: TrendingTagsViewModel.TrendingTagsUiState
    ) {
        Log.d(TAG, uiState.loadingState.name)
        when (uiState.loadingState) {
            TrendingTagsViewModel.LoadingState.INITIAL -> clearLoadingState()
            TrendingTagsViewModel.LoadingState.LOADING -> applyLoadingState()
            TrendingTagsViewModel.LoadingState.REFRESHING -> applyRefreshingState()
            TrendingTagsViewModel.LoadingState.LOADED -> applyLoadedState(adapter, uiState.trendingViewData)
            TrendingTagsViewModel.LoadingState.ERROR_NETWORK -> networkError()
            TrendingTagsViewModel.LoadingState.ERROR_OTHER -> otherError()
        }
    }

    private fun applyLoadedState(adapter: TrendingTagsAdapter, viewData: List<TrendingViewData>) {
        clearLoadingState()

        adapter.submitList(viewData)

        if (viewData.isEmpty()) {
            binding.recyclerView.hide()
            binding.messageView.show()
            binding.messageView.setup(
                R.drawable.elephant_friend_empty,
                R.string.message_empty,
                null
            )
        } else {
            binding.recyclerView.show()
            binding.messageView.hide()
        }
        binding.progressBar.hide()
    }

    private fun applyRefreshingState() {
        binding.swipeRefreshLayout.isRefreshing = true
    }

    private fun applyLoadingState() {
        binding.recyclerView.hide()
        binding.messageView.hide()
        binding.progressBar.show()
    }

    private fun clearLoadingState() {
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.hide()
        binding.messageView.hide()
    }

    private fun networkError() {
        binding.recyclerView.hide()
        binding.messageView.show()
        binding.progressBar.hide()

        binding.swipeRefreshLayout.isRefreshing = false
        binding.messageView.setup(
            R.drawable.errorphant_offline,
            R.string.error_network
        ) { refreshContent() }
    }

    private fun otherError() {
        binding.recyclerView.hide()
        binding.messageView.show()
        binding.progressBar.hide()

        binding.swipeRefreshLayout.isRefreshing = false
        binding.messageView.setup(
            R.drawable.errorphant_error,
            R.string.error_generic
        ) { refreshContent() }
    }

    private fun actionButtonPresent(): Boolean {
        return activity is ActionButtonActivity
    }

    private var talkBackWasEnabled = false

    override fun onResume() {
        super.onResume()
        val a11yManager = requireContext().getSystemService<AccessibilityManager>()

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Log.d(TAG, "talkback was enabled: $wasEnabled, now $talkBackWasEnabled")
        if (talkBackWasEnabled && !wasEnabled) {
            val adapter = requireNotNull(this.adapter)
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        if (actionButtonPresent()) {
            val composeButton = (activity as ActionButtonActivity).actionButton
            composeButton?.hide()
        }
    }

    override fun onReselect() {
        if (view != null) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun refreshContent() {
        onRefresh()
    }

    companion object {
        private const val TAG = "TrendingTagsFragment"

        fun newInstance() = TrendingTagsFragment()
    }
}
