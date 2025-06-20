/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.timeline

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.sparkbutton.SparkButton
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.updateRelativeTimePeriodically
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.ConfirmationBottomSheet.Companion.confirmFavourite
import com.keylesspalace.tusky.view.ConfirmationBottomSheet.Companion.confirmReblog
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimelineFragment :
    SFragment(R.layout.fragment_timeline),
    OnRefreshListener,
    StatusActionListener,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: TimelineViewModel by unsafeLazy {
        val viewModelProvider = ViewModelProvider(viewModelStore, defaultViewModelProviderFactory, defaultViewModelCreationExtras)
        if (kind == TimelineViewModel.Kind.HOME) {
            viewModelProvider[CachedTimelineViewModel::class.java]
        } else {
            viewModelProvider[NetworkTimelineViewModel::class.java]
        }
    }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var kind: TimelineViewModel.Kind

    private var adapter: TimelinePagingAdapter? = null

    private var isSwipeToRefreshEnabled = true

    /**
     * Adapter position of the placeholder that was most recently clicked to "Load more". If null
     * then there is no active "Load more" operation
     */
    private var loadMorePosition: Int? = null

    /** ID of the status immediately below the most recent "Load more" placeholder click */
    // The Paging library assumes that the user will be scrolling down a list of items,
    // and if new items are loaded but not visible then it's reasonable to scroll to the top
    // of the inserted items. It does not seem to be possible to disable that behaviour.
    //
    // That behaviour should depend on the user's preferred reading order. If they prefer to
    // read oldest first then the list should be scrolled to the bottom of the freshly
    // inserted statuses.
    //
    // To do this:
    //
    // 1. When "Load more" is clicked (onLoadMore()):
    //    a. Remember the adapter position of the "Load more" item in loadMorePosition
    //    b. Remember the ID of the status immediately below the "Load more" item in
    //       statusIdBelowLoadMore
    // 2. After the new items have been inserted, search the adapter for the position of the
    //    status with id == statusIdBelowLoadMore.
    // 3. If this position is still visible on screen then do nothing, otherwise, scroll the view
    //    so that the status is visible.
    //
    // The user can then scroll up to read the new statuses.
    private var statusIdBelowLoadMore: String? = null

    /** The user's preferred reading order */
    private lateinit var readingOrder: ReadingOrder

    private var buttonToAnimate: SparkButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = requireArguments()
        kind = TimelineViewModel.Kind.valueOf(arguments.getString(KIND_ARG)!!)
        val id: String? = if (kind == TimelineViewModel.Kind.USER ||
            kind == TimelineViewModel.Kind.USER_PINNED ||
            kind == TimelineViewModel.Kind.USER_WITH_REPLIES ||
            kind == TimelineViewModel.Kind.LIST
        ) {
            arguments.getString(ID_ARG)!!
        } else {
            null
        }

        val tags = if (kind == TimelineViewModel.Kind.TAG) {
            arguments.getStringArrayList(HASHTAGS_ARG)!!
        } else {
            listOf()
        }
        viewModel.init(
            kind,
            id,
            tags
        )

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        readingOrder = ReadingOrder.from(preferences.getString(PrefKeys.READING_ORDER, null))
    }

    private fun createAdapter(): TimelinePagingAdapter {
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
            ) {
                CardViewMode.INDENTED
            } else {
                CardViewMode.NONE
            },
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )
        return TimelinePagingAdapter(
            statusDisplayOptions,
            this
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val adapter = createAdapter()
        this.adapter = adapter

        setupSwipeRefreshLayout()
        setupRecyclerView(adapter)

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            binding.statusView.hide()
            binding.progressBar.hide()

            if (adapter.itemCount == 0) {
                when (loadState.refresh) {
                    is LoadState.NotLoading -> {
                        if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                            binding.statusView.show()
                            binding.statusView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty
                            )
                            if (kind == TimelineViewModel.Kind.HOME) {
                                binding.statusView.showHelp(R.string.help_empty_home)
                            }
                        }
                    }

                    is LoadState.Error -> {
                        binding.statusView.show()
                        binding.statusView.setup(
                            (loadState.refresh as LoadState.Error).error
                        ) { onRefresh() }
                    }

                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val firstPos = (binding.recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                if (firstPos == 0 && positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            if (isSwipeToRefreshEnabled) {
                                binding.recyclerView.scrollBy(
                                    0,
                                    Utils.dpToPx(requireContext(), -30)
                                )
                            } else {
                                binding.recyclerView.scrollToPosition(0)
                            }
                        }
                    }
                    // we loaded new posts at the top - no need to handle "load more" anymore
                    loadMorePosition = null
                }
                if (readingOrder == ReadingOrder.OLDEST_FIRST) {
                    updateReadingPositionForOldestFirst(adapter)
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statuses.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            eventHub.events.collect { event ->
                when (event) {
                    is PreferenceChangedEvent -> {
                        onPreferenceChanged(adapter, event.preferenceKey)
                    }

                    is StatusComposedEvent -> {
                        val status = event.status
                        handleStatusComposeEvent(adapter, status)
                    }
                }
            }
        }

        updateRelativeTimePeriodically(preferences, adapter)
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        buttonToAnimate = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        if (isSwipeToRefreshEnabled) {
            menuInflater.inflate(R.menu.fragment_timeline, menu)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                if (isSwipeToRefreshEnabled) {
                    binding.swipeRefreshLayout.isRefreshing = true

                    refreshContent()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    /**
     * Set the correct reading position in the timeline after the user clicked "Load more",
     * assuming the reading position should be below the freshly-loaded statuses.
     */
    // Note: The positionStart parameter to onItemRangeInserted() does not always
    // match the adapter position where data was inserted (which is why loadMorePosition
    // is tracked manually, see this bug report for another example:
    // https://github.com/android/architecture-components-samples/issues/726).
    private fun updateReadingPositionForOldestFirst(adapter: TimelinePagingAdapter) {
        var position = loadMorePosition ?: return
        val statusIdBelowLoadMore = statusIdBelowLoadMore ?: return

        var status: StatusViewData?
        while (adapter.peek(position).let {
                status = it
                it != null
            }
        ) {
            if (status?.id == statusIdBelowLoadMore) {
                val lastVisiblePosition =
                    (binding.recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (position > lastVisiblePosition) {
                    binding.recyclerView.scrollToPosition(position)
                }
                break
            }
            position++
        }
        loadMorePosition = null
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    private fun setupRecyclerView(adapter: TimelinePagingAdapter) {
        val hasFab = (activity as? ActionButtonActivity?)?.actionButton != null
        binding.recyclerView.ensureBottomPadding(fab = hasFab)

        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.recyclerView, this) { pos ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        val divider = DividerItemDecoration(context, RecyclerView.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    override fun onRefresh() {
        binding.statusView.hide()

        adapter?.refresh()
    }

    override val onMoreTranslate =
        { translate: Boolean, position: Int ->
            if (translate) {
                onTranslate(position)
            } else {
                onUntranslate(
                    position
                )
            }
        }

    override fun onReply(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.reply(status.status)
    }

    override fun onReblog(reblog: Boolean, position: Int, visibility: Status.Visibility?, button: SparkButton?) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        buttonToAnimate = button

        if (reblog && visibility == null) {
            confirmReblog(preferences) { visibility ->
                viewModel.reblog(true, status, visibility)
                buttonToAnimate?.playAnimation()
                buttonToAnimate?.isChecked = true
            }
        } else {
            viewModel.reblog(reblog, status, visibility ?: Status.Visibility.PUBLIC)
            if (reblog) {
                buttonToAnimate?.playAnimation()
            }
            buttonToAnimate?.isChecked = reblog
        }
    }

    private fun onTranslate(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.translate(status)
                .onFailure {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.ui_error_translate, it.message),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    override fun onUntranslate(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.untranslate(status)
    }

    override fun onFavourite(favourite: Boolean, position: Int, button: SparkButton?) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        buttonToAnimate = button

        if (favourite) {
            confirmFavourite(preferences) {
                viewModel.favorite(true, status)
                buttonToAnimate?.playAnimation()
                buttonToAnimate?.isChecked = true
            }
        } else {
            viewModel.favorite(false, status)
        }
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.bookmark(bookmark, status)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.voteInPoll(choices, status)
    }

    override fun onShowPollResults(position: Int) {
        adapter?.peek(position)?.asStatusOrNull()?.let { status ->
            viewModel.showPollResults(status)
        }
    }

    override fun clearWarningAction(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.clearWarning(status)
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.more(
            status.status,
            view,
            position,
            (status.translation as? TranslationViewData.Loaded)?.data
        )
    }

    override fun onOpenReblog(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.openReblog(status.status)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeExpanded(expanded, status)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentShowing(isShowing, status)
    }

    override fun onShowReblogs(position: Int) {
        val statusId = adapter?.peek(position)?.asStatusOrNull()?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        activity?.startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = adapter?.peek(position)?.asStatusOrNull()?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        activity?.startActivityWithSlideInAnimation(intent)
    }

    override fun onLoadMore(position: Int) {
        val adapter = this.adapter
        val placeholder = adapter?.peek(position)?.asPlaceholderOrNull() ?: return
        loadMorePosition = position
        statusIdBelowLoadMore =
            if (position + 1 < adapter.itemCount) adapter.peek(position + 1)?.id else null
        viewModel.loadMore(placeholder.id)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentCollapsed(isCollapsed, status)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.viewMedia(
            attachmentIndex,
            AttachmentViewData.list(status),
            view
        )
    }

    override fun onViewThread(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.viewThread(status.actionable.id, status.actionable.url)
    }

    override fun onViewTag(tag: String) {
        if (viewModel.kind == TimelineViewModel.Kind.TAG && viewModel.tags.size == 1 &&
            viewModel.tags.contains(tag)
        ) {
            // If already viewing a tag page, then ignore any request to view that tag again.
            return
        }
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        if ((
                viewModel.kind == TimelineViewModel.Kind.USER ||
                    viewModel.kind == TimelineViewModel.Kind.USER_WITH_REPLIES
                ) &&
            viewModel.id == id
        ) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return
        }
        super.viewAccount(id)
    }

    private fun onPreferenceChanged(adapter: TimelinePagingAdapter, key: String) {
        when (key) {
            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }

            PrefKeys.READING_ORDER -> {
                readingOrder = ReadingOrder.from(
                    preferences.getString(PrefKeys.READING_ORDER, null)
                )
            }
        }
    }

    private fun handleStatusComposeEvent(adapter: TimelinePagingAdapter, status: Status) {
        when (kind) {
            TimelineViewModel.Kind.HOME,
            TimelineViewModel.Kind.PUBLIC_FEDERATED,
            TimelineViewModel.Kind.PUBLIC_LOCAL,
            TimelineViewModel.Kind.PUBLIC_TRENDING_STATUSES -> adapter.refresh()

            TimelineViewModel.Kind.USER,
            TimelineViewModel.Kind.USER_WITH_REPLIES -> if (status.account.id == viewModel.id) {
                adapter.refresh()
            }

            TimelineViewModel.Kind.TAG,
            TimelineViewModel.Kind.FAVOURITES,
            TimelineViewModel.Kind.LIST,
            TimelineViewModel.Kind.BOOKMARKS,
            TimelineViewModel.Kind.USER_PINNED -> return
        }
    }

    public override fun removeItem(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.removeStatusWithId(status.id)
    }

    private var talkBackWasEnabled = false

    override fun onPause() {
        super.onPause()
        (binding.recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
            ?.let { position ->
                if (position != RecyclerView.NO_POSITION) {
                    adapter?.snapshot()?.getOrNull(position)?.id?.let { statusId ->
                        viewModel.saveReadingPosition(statusId)
                    }
                }
            }
    }

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
        private const val TAG = "TimelineF" // logging tag
        private const val KIND_ARG = "kind"
        private const val ID_ARG = "id"
        private const val HASHTAGS_ARG = "hashtags"
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "enableSwipeToRefresh"

        fun newInstance(
            kind: TimelineViewModel.Kind,
            hashtagOrId: String? = null,
            enableSwipeToRefresh: Boolean = true
        ): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(3)
            arguments.putString(KIND_ARG, kind.name)
            arguments.putString(ID_ARG, hashtagOrId)
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, enableSwipeToRefresh)
            fragment.arguments = arguments
            return fragment
        }

        @JvmStatic
        fun newHashtagInstance(hashtags: List<String>): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(3)
            arguments.putString(KIND_ARG, TimelineViewModel.Kind.TAG.name)
            arguments.putStringArrayList(HASHTAGS_ARG, ArrayList(hashtags))
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)
            fragment.arguments = arguments
            return fragment
        }
    }
}
