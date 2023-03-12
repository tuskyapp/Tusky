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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import autodispose2.androidx.lifecycle.autoDispose
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusEditedEvent
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.components.notifications.StatusActionSuccess
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.StatusAction
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class TimelineFragment :
    SFragment(),
    OnRefreshListener,
    StatusActionListener,
    Injectable,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var eventHub: EventHub

    private val viewModel: TimelineViewModel by unsafeLazy {
        if (kind == TimelineViewModel.Kind.HOME) {
            ViewModelProvider(this, viewModelFactory)[CachedTimelineViewModel::class.java]
        } else {
            ViewModelProvider(this, viewModelFactory)[NetworkTimelineViewModel::class.java]
        }
    }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var kind: TimelineViewModel.Kind

    private lateinit var adapter: TimelinePagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

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
            tags,
        )

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        adapter = TimelinePagingAdapter(
            viewModel.statusDisplayOptions.value,
            this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        layoutManager = LinearLayoutManager(context)

        setupSwipeRefreshLayout()
        setupRecyclerView()

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
                            binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty)
                        }
                    }
                    is LoadState.Error -> {
                        binding.statusView.show()

                        if ((loadState.refresh as LoadState.Error).error is IOException) {
                            binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network)
                        } else {
                            binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic)
                        }
                    }
                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            if (isSwipeToRefreshEnabled) {
                                binding.recyclerView.scrollBy(0, Utils.dpToPx(requireContext(), -30))
                            } else binding.recyclerView.scrollToPosition(0)
                        }
                    }
                }
                if (viewModel.uiState.value.readingOrder == ReadingOrder.OLDEST_FIRST) {
                    updateReadingPositionForOldestFirst()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statuses.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        if (actionButtonPresent()) {
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                    val composeButton = (activity as ActionButtonActivity).actionButton
                    if (composeButton != null) {
                        if (!viewModel.uiState.value.showFabWhileScrolling) {
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

        /**
         * Collect this flow to notify the adapter that the timestamps of the visible items have
         * changed
         */
        // TODO: Copied from NotificationsFragment
        val updateTimestampFlow = flow {
            while (true) { delay(60000); emit(Unit) }
        }.onEach {
            layoutManager.findFirstVisibleItemPosition().let { first ->
                first == RecyclerView.NO_POSITION && return@let
                val count = layoutManager.findLastVisibleItemPosition() - first
                adapter.notifyItemRangeChanged(
                    first,
                    count,
                    listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Show errors from the view model as snack bars.
                    //
                    // Errors are shown:
                    // - Indefinitely, so the user has a chance to read and understand
                    //   the message
                    // - With a max of 5 text lines, to allow space for longer errors.
                    //   E.g., on a typical device, an error message like "Bookmarking
                    //   post failed: Unable to resolve host 'mastodon.social': No
                    //   address associated with hostname" is 3 lines.
                    // - With a "Retry" option if the error included a UiAction to retry.
                    // TODO: Very similar to same code in NotificationsFragment
                    launch {
                        viewModel.uiError.collect { error ->
                            Log.d(TAG, error.toString())
                            val message = getString(
                                error.message,
                                error.exception.localizedMessage
                                    ?: getString(R.string.ui_error_unknown)
                            )
                            val snackbar = Snackbar.make(
                                // Without this the FAB will not move out of the way
                                (activity as ActionButtonActivity).actionButton ?: binding.root,
                                message,
                                Snackbar.LENGTH_INDEFINITE
                            ).setTextMaxLines(5)
                            error.action?.let { action ->
                                snackbar.setAction(R.string.action_retry) {
                                    viewModel.accept(action)
                                }
                            }
                            snackbar.show()

                            // The status view has pre-emptively updated its state to show
                            // that the action succeeded. Since it hasn't, re-bind the view
                            // to show the correct data.
                            error.action?.let { action ->
                                if (action !is StatusAction) return@let

                                val position = adapter.snapshot().indexOfFirst {
                                    it?.id == action.statusViewData.id
                                }
                                if (position != RecyclerView.NO_POSITION) {
                                    adapter.notifyItemChanged(position)
                                }
                            }
                        }
                    }

                    // Update adapter data when status actions are successful, and re-bind to update
                    // the UI.
                    launch {
                        viewModel.uiSuccess
                            .filterIsInstance<StatusActionSuccess>()
                            .collect {
                                val indexedViewData = adapter.snapshot()
                                    .withIndex()
                                    .firstOrNull { indexed ->
                                        indexed.value?.id == it.action.statusViewData.id
                                    } ?: return@collect

                                val statusViewData=
                                    indexedViewData.value as? StatusViewData.Concrete ?: return@collect

                                val status = when (it) {
                                    is StatusActionSuccess.Bookmark ->
                                        statusViewData.status.copy(bookmarked = it.action.state)
                                    is StatusActionSuccess.Favourite ->
                                        statusViewData.status.copy(favourited = it.action.state)
                                    is StatusActionSuccess.Reblog ->
                                        statusViewData.status.copy(reblogged = it.action.state)
                                    is StatusActionSuccess.VoteInPoll ->
                                        statusViewData.status.copy(
                                            poll = it.action.poll.votedCopy(it.action.choices)
                                        )
                                }
                                (indexedViewData.value as StatusViewData.Concrete).status = status

                                adapter.notifyItemChanged(indexedViewData.index)
                            }
                    }

                    viewModel.uiState.collectLatest {
                        // showMediaPreview changed?
                        val previousMediaPreview = adapter.mediaPreviewEnabled
                        if (previousMediaPreview != it.showMediaPreview) {
                            adapter.mediaPreviewEnabled = it.showMediaPreview
                            adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        }
                    }
                }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically re-bind the UI.
                // TODO: Copied from NotificationsFragment
                launch {
                    viewModel.statusDisplayOptions
                        .collectLatest {
                            // TODO: TimelinePagingAdapter doesn't handle statusDisplayOptions
                            // the same way NotificationsPagingAdapter does. Investigate bringing
                            // the two classes in to alignment.
//                            adapter.statusDisplayOptions = it
//                            layoutManager.findFirstVisibleItemPosition().let { first ->
//                                first == RecyclerView.NO_POSITION && return@let
//                                val count = layoutManager.findLastVisibleItemPosition() - first
//                                adapter.notifyItemRangeChanged(
//                                    first,
//                                    count,
//                                    null
//                                )
//                            }

                            if (!it.useAbsoluteTime) {
                                updateTimestampFlow.collect()
                            }
                        }
                }
            }
        }

        eventHub.events
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(this, Lifecycle.Event.ON_DESTROY)
            .subscribe { event ->
                when (event) {
                    is StatusComposedEvent -> {
                        val status = event.status
                        handleStatusComposeEvent(status)
                    }
                    is StatusEditedEvent -> {
                        handleStatusComposeEvent(event.status)
                    }
                }
            }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        if (isSwipeToRefreshEnabled) {
            menuInflater.inflate(R.menu.fragment_timeline, menu)
            menu.findItem(R.id.action_refresh)?.apply {
                icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                    sizeDp = 20
                    colorInt =
                        MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
                }
            }
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
    private fun updateReadingPositionForOldestFirst() {
        var position = loadMorePosition ?: return
        val statusIdBelowLoadMore = statusIdBelowLoadMore ?: return

        var status: StatusViewData?
        while (adapter.peek(position).let { status = it; it != null }) {
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
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.recyclerView, this) { pos ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            }
        )
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = layoutManager
        val divider = DividerItemDecoration(context, RecyclerView.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    override fun onRefresh() {
        binding.statusView.hide()

        adapter.refresh()
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.reply(status.status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val statusViewData = adapter.peek(position) as? StatusViewData.Concrete ?: return
        viewModel.accept(StatusAction.Reblog(reblog, statusViewData))
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val statusViewData = adapter.peek(position) as? StatusViewData.Concrete ?: return
        viewModel.accept(StatusAction.Favourite(favourite, statusViewData))
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val statusViewData = adapter.peek(position) as? StatusViewData.Concrete ?: return
        viewModel.accept(StatusAction.Bookmark(bookmark, statusViewData))
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val statusViewData = adapter.peek(position) as? StatusViewData.Concrete ?: return
        val poll = statusViewData.status.poll ?: return
        viewModel.accept(StatusAction.VoteInPoll(poll, choices, statusViewData))
    }

    override fun clearWarningAction(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.clearWarning(status)
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.more(status.status, view, position)
    }

    override fun onOpenReblog(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.openReblog(status.status)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeExpanded(expanded, status)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentShowing(isShowing, status)
    }

    override fun onShowReblogs(position: Int) {
        val statusId = adapter.peek(position)?.asStatusOrNull()?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = adapter.peek(position)?.asStatusOrNull()?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onLoadMore(position: Int) {
        val placeholder = adapter.peek(position)?.asPlaceholderOrNull() ?: return
        loadMorePosition = position
        statusIdBelowLoadMore = adapter.peek(position + 1)?.id
        viewModel.loadMore(placeholder.id)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentCollapsed(isCollapsed, status)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.viewMedia(
            attachmentIndex,
            AttachmentViewData.list(status.actionable),
            view
        )
    }

    override fun onViewThread(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
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

    private fun handleStatusComposeEvent(status: Status) {
        when (kind) {
            TimelineViewModel.Kind.HOME,
            TimelineViewModel.Kind.PUBLIC_FEDERATED,
            TimelineViewModel.Kind.PUBLIC_LOCAL -> adapter.refresh()
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
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.removeStatusWithId(status.id)
    }

    private fun actionButtonPresent(): Boolean {
        return viewModel.kind != TimelineViewModel.Kind.TAG &&
            viewModel.kind != TimelineViewModel.Kind.FAVOURITES &&
            viewModel.kind != TimelineViewModel.Kind.BOOKMARKS &&
            activity is ActionButtonActivity
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
