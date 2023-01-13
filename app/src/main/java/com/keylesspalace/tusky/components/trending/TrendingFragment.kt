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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import autodispose2.androidx.lifecycle.autoDispose
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.PostLookupFallbackBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.trending.viewmodel.TrendingViewModel
import com.keylesspalace.tusky.databinding.FragmentTrendingBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.TrendingViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
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

    private lateinit var adapter: TrendingPagingAdapter

    private var isSwipeToRefreshEnabled = true
    private var hideFab = false

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

        val arguments = requireArguments()

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = if (preferences.getBoolean(
                    PrefKeys.SHOW_CARDS_IN_TIMELINES,
                    false
                )
            ) CardViewMode.INDENTED else CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )
        adapter = TrendingPagingAdapter(
            statusDisplayOptions,
            this,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount =
            requireContext().resources.getInteger(R.integer.trending_column_count)
        binding.recyclerView.layoutManager = GridLayoutManager(context, columnCount)
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
                            if (isSwipeToRefreshEnabled) {
                                binding.recyclerView.scrollBy(
                                    0,
                                    Utils.dpToPx(requireContext(), -30)
                                )
                            } else binding.recyclerView.scrollToPosition(0)
                        }
                    }
                }
            }
        })

        if (actionButtonPresent()) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            hideFab = preferences.getBoolean("fabHide", false)
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                    val composeButton = (activity as ActionButtonActivity).actionButton
                    if (composeButton != null) {
                        if (hideFab) {
                            if (dy > 0 && composeButton.isShown) {
                                composeButton.hide() // hides the button if we're scrolling down
                            } else if (dy < 0 && !composeButton.isShown) {
                                composeButton.show() // shows it if we are scrolling up
                            }
                        } else if (!composeButton.isShown) {
                            composeButton.show()
                        }
                    }
                }
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { trendingState ->
                processViewState(trendingState)
            }
        }

        eventHub.events
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(this, Lifecycle.Event.ON_DESTROY)
            .subscribe { event ->
                when (event) {
                    is PreferenceChangedEvent -> {
                        onPreferenceChanged(event.preferenceKey)
                    }
                }
            }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun setupRecyclerView() {
        val columnCount =
            requireContext().resources.getInteger(R.integer.trending_column_count)
        binding.recyclerView.layoutManager = GridLayoutManager(context, columnCount)

        binding.recyclerView.setHasFixedSize(true)
        val divider = DividerItemDecoration(context, RecyclerView.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
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
        startActivity(StatusListActivity.newHashtagIntent(requireContext(), tag))
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
        adapter.submitList(viewData)

        if (viewData.isEmpty()) {
            binding.recyclerView.hide()
            binding.messageView.show()
            binding.messageView.setup(
                R.drawable.elephant_friend_empty, R.string.message_empty,
                null
            )
        } else {
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

    private fun onPreferenceChanged(key: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        when (key) {
            PrefKeys.FAB_HIDE -> {
                hideFab = sharedPreferences.getBoolean(PrefKeys.FAB_HIDE, false)
            }

            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }
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
        private const val TAG = "TrendingF" // logging tag
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "enableSwipeToRefresh"

        fun newInstance(): TrendingFragment {
            val fragment = TrendingFragment()
            val arguments = Bundle(1)
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)
            fragment.arguments = arguments
            return fragment
        }
    }
}
