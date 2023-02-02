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

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.PostLookupFallbackBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.trending.viewmodel.TrendingViewModel
import com.keylesspalace.tusky.databinding.FragmentTrendingBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.TrendingViewData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class TrendingFragment :
    Fragment(),
    OnRefreshListener,
    LinkListener,
    Injectable,
    ReselectableFragment,
    RefreshableFragment {

    private lateinit var bottomSheetActivity: BottomSheetActivity

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var eventHub: EventHub

    private val viewModel: TrendingViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[TrendingViewModel::class.java]
    }

    private val binding by viewBinding(FragmentTrendingBinding::bind)

    private lateinit var adapter: TrendingAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetActivity = if (context is BottomSheetActivity) {
            context
        } else {
            throw IllegalStateException("Fragment must be attached to a BottomSheetActivity!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = TrendingAdapter(
            this,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount =
            requireContext().resources.getInteger(R.integer.trending_column_count)
        setupLayoutManager(columnCount)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_trending, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSwipeRefreshLayout()
        setupRecyclerView()

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0 && adapter.itemCount != itemCount) {
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
                processViewState(trendingState)
            }
        }

        if (activity is ActionButtonActivity) {
            (activity as ActionButtonActivity).actionButton?.visibility = View.GONE
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun setupLayoutManager(columnCount: Int) {
        binding.recyclerView.layoutManager = GridLayoutManager(context, columnCount).apply {
            spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        TrendingAdapter.VIEW_TYPE_HEADER -> columnCount
                        TrendingAdapter.VIEW_TYPE_TAG -> 1
                        else -> -1
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val columnCount =
            requireContext().resources.getInteger(R.integer.trending_column_count)
        setupLayoutManager(columnCount)

        binding.recyclerView.setHasFixedSize(true)

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    override fun onRefresh() {
        viewModel.invalidate()
    }

    override fun onViewUrl(url: String) {
        bottomSheetActivity.viewUrl(url, PostLookupFallbackBehavior.OPEN_IN_BROWSER)
    }

    override fun onViewTag(tag: String) {
        bottomSheetActivity.startActivityWithSlideInAnimation(StatusListActivity.newHashtagIntent(requireContext(), tag))
    }

    override fun onViewAccount(id: String) {
        bottomSheetActivity.viewAccount(id)
    }

    private fun processViewState(uiState: TrendingViewModel.TrendingUiState) {
        when (uiState.loadingState) {
            TrendingViewModel.LoadingState.INITIAL -> clearLoadingState()
            TrendingViewModel.LoadingState.LOADING -> applyLoadingState()
            TrendingViewModel.LoadingState.LOADED -> applyLoadedState(uiState.trendingViewData)
            TrendingViewModel.LoadingState.ERROR_NETWORK -> networkError()
            TrendingViewModel.LoadingState.ERROR_OTHER -> otherError()
        }
    }

    private fun applyLoadedState(viewData: List<TrendingViewData>) {
        clearLoadingState()

        if (viewData.isEmpty()) {
            adapter.submitList(emptyList())
            binding.recyclerView.hide()
            binding.messageView.show()
            binding.messageView.setup(
                R.drawable.elephant_friend_empty, R.string.message_empty,
                null
            )
        } else {
            val viewDataWithDates = listOf(viewData.first().asHeaderOrNull()) + viewData

            adapter.submitList(viewDataWithDates)

            binding.recyclerView.show()
            binding.messageView.hide()
        }
        binding.progressBar.hide()
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
            R.drawable.elephant_offline,
            R.string.error_network,
        ) { refreshContent() }
    }

    private fun otherError() {
        binding.recyclerView.hide()
        binding.messageView.show()
        binding.progressBar.hide()

        binding.swipeRefreshLayout.isRefreshing = false
        binding.messageView.setup(
            R.drawable.elephant_error,
            R.string.error_generic,
        ) { refreshContent() }
    }

    private fun actionButtonPresent(): Boolean {
        return activity is ActionButtonActivity
    }

    private var talkBackWasEnabled = false

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Log.d(TAG, "talkback was enabled: $wasEnabled, now $talkBackWasEnabled")
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        if (actionButtonPresent()) {
            val composeButton = (activity as ActionButtonActivity).actionButton
            composeButton?.hide()
        }
    }

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun refreshContent() {
        onRefresh()
    }

    companion object {
        private const val TAG = "TrendingFragment"

        fun newInstance(): TrendingFragment {
            return TrendingFragment()
        }
    }
}
