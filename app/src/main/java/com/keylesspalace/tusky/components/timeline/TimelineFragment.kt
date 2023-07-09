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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.InfallibleUiAction
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.StatusAction
import com.keylesspalace.tusky.components.timeline.viewmodel.StatusActionSuccess
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.UiSuccess
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
import com.keylesspalace.tusky.util.getDrawableRes
import com.keylesspalace.tusky.util.getErrorString
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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

    private val viewModel: TimelineViewModel by unsafeLazy {
        if (timelineKind == TimelineKind.Home) {
            ViewModelProvider(this, viewModelFactory)[CachedTimelineViewModel::class.java]
        } else {
            ViewModelProvider(this, viewModelFactory)[NetworkTimelineViewModel::class.java]
        }
    }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var timelineKind: TimelineKind

    private lateinit var adapter: TimelinePagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

    /** The active snackbar, if any */
    // TODO: This shouldn't be necessary, the snackbar should dismiss itself if the layout
    // changes. It doesn't, because the CoordinatorLayout is in the activity, not the fragment.
    // I think the correct fix is to include the FAB in each fragment layout that needs it,
    // ensuring that the outermost fragment view is a CoordinatorLayout. That will auto-dismiss
    // the snackbar when the fragment is paused.
    private var snackbar: Snackbar? = null

    private var isSwipeToRefreshEnabled = true

    /** True if the reading position should be restored when new data is submitted to the adapter */
    private var shouldRestoreReadingPosition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = requireArguments()

        timelineKind = arguments.getParcelable(KIND_ARG)!!

        shouldRestoreReadingPosition = timelineKind == TimelineKind.Home

        viewModel.init(timelineKind)

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        adapter = TimelinePagingAdapter(this, viewModel.statusDisplayOptions.value)
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

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    newState != SCROLL_STATE_IDLE && return
                    saveVisibleId()
                }
            })
        }

        /**
         * Collect this flow to notify the adapter that the timestamps of the visible items have
         * changed
         */
        // TODO: Copied from NotificationsFragment
        val updateTimestampFlow = flow {
            while (true) { delay(60.seconds); emit(Unit) }
        }.onEach {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.statuses.collectLatest { pagingData ->
                            adapter.submitData(pagingData)
                        }
                    }

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
                                error.throwable.localizedMessage
                                    ?: getString(R.string.ui_error_unknown)
                            )
                            snackbar = Snackbar.make(
                                // Without this the FAB will not move out of the way
                                (activity as ActionButtonActivity).actionButton ?: binding.root,
                                message,
                                Snackbar.LENGTH_INDEFINITE
                            ).setTextMaxLines(5)
                            error.action?.let { action ->
                                snackbar!!.setAction(R.string.action_retry) {
                                    viewModel.accept(action)
                                }
                            }
                            snackbar!!.show()

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

                                val statusViewData =
                                    indexedViewData.value ?: return@collect

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
                                (indexedViewData.value as StatusViewData).status = status

                                adapter.notifyItemChanged(indexedViewData.index)
                            }
                    }

                    // Refresh adapter on mutes and blocks
                    launch {
                        viewModel.uiSuccess.collectLatest {
                            when (it) {
                                is UiSuccess.Block,
                                is UiSuccess.Mute,
                                is UiSuccess.MuteConversation ->
                                    adapter.refresh()

                                is UiSuccess.StatusSent -> handleStatusSentOrEdit(it.status)
                                is UiSuccess.StatusEdited -> handleStatusSentOrEdit(it.status)

                                else -> { /* nothing to do */ }
                            }
                        }
                    }
                }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically re-bind the UI.
                launch {
                    viewModel.statusDisplayOptions
                        .collectLatest {
                            adapter.statusDisplayOptions = it
                            layoutManager.findFirstVisibleItemPosition().let { first ->
                                first == RecyclerView.NO_POSITION && return@let
                                val count = layoutManager.findLastVisibleItemPosition() - first
                                adapter.notifyItemRangeChanged(
                                    first,
                                    count,
                                    null
                                )
                            }

                            if (!it.useAbsoluteTime) {
                                updateTimestampFlow.collect()
                            }
                        }
                }

                // Restore the user's reading position, if appropriate.
                // Collect the first page submitted to the adapter, which will be the Refresh.
                // This should contain a status with an ID that matches the reading position.
                // Find that status and scroll to it.
                launch {
                    if (shouldRestoreReadingPosition) {
                        adapter.onPagesUpdatedFlow.take(1).collect()
                        Log.d(TAG, "Page updated, should restore reading position")
                        adapter.snapshot()
                            .indexOfFirst { it?.id == viewModel.readingPositionId }
                            .takeIf { it != -1 }
                            ?.let { pos ->
                                binding.recyclerView.post {
                                    getView() ?: return@post
                                    binding.recyclerView.scrollToPosition(pos)
                                }
                            }
                        shouldRestoreReadingPosition = false
                    }
                }

                // Scroll the list down if a refresh has completely finished. A refresh is
                // finished when both the initial refresh is complete and any prepends have
                // finished (so that DiffUtil has had a chance to process the data). See
                // https://github.com/googlecodelabs/android-paging/issues/149
                launch {
                    var old: CombinedLoadStates? = null
                    var refreshComplete = false

                    if (isSwipeToRefreshEnabled) {
                        adapter.loadStateFlow
                            .collect {
                                if (old == null) {
                                    Log.d("loadState", "No previous loadState")
                                    old = it
                                    return@collect
                                }

                                if (old?.refresh != it.refresh) {
                                    Log.d("loadState", "refresh: ${old?.refresh} -> ${it.refresh}")
                                }

                                if (old?.prepend != it.prepend) {
                                    Log.d("loadState", "prepend: ${old?.prepend} -> ${it.prepend}")
                                }

                                if (old?.append != it.append) {
                                    Log.d("loadState", "append: ${old?.append} -> ${it.append}")
                                }

                                if (old?.source?.refresh != it.source.refresh) {
                                    Log.d("loadState", "  source.refresh: ${old?.source?.refresh} -> ${it.source.refresh}")
                                }

                                if (old?.source?.prepend != it.source.prepend) {
                                    Log.d("loadState", "  source.prepend: ${old?.source?.prepend} -> ${it.source.prepend}")
                                }

                                if (old?.source?.append != it.source.append) {
                                    Log.d("loadState", "  source.append: ${old?.source?.append} -> ${it.source.append}")
                                }

                                if (old?.mediator?.refresh != it.mediator?.refresh) {
                                    Log.d("loadState", "  mediator.refresh: ${old?.mediator?.refresh} -> ${it.mediator?.refresh}")
                                }

                                if (old?.mediator?.prepend != it.mediator?.prepend) {
                                    Log.d("loadState", "  mediator.prepend: ${old?.mediator?.prepend} -> ${it.mediator?.prepend}")
                                }

                                if (old?.mediator?.append != it.mediator?.append) {
                                    Log.d("loadState", "  mediator.append: ${old?.mediator?.append} -> ${it.mediator?.append}")
                                }

                                if (!refreshComplete) {
                                    refreshComplete =
                                        old?.refresh is LoadState.Loading && it.refresh is LoadState.NotLoading
                                }

                                if (refreshComplete) {
                                    if (old?.prepend is LoadState.Loading && it.prepend is LoadState.NotLoading) {
                                        refreshComplete = false
                                        Log.d("loadState", "mediator.prepend=NotLoading, scrolling to peek")
                                        binding.recyclerView.post {
                                            getView() ?: return@post
                                            binding.recyclerView.scrollBy(0, Utils.dpToPx(requireContext(), -30))
                                        }
                                    }
                                }
                                old = it
                            }
                    }
                }

                // Update the UI from the combined load state
                adapter.loadStateFlow
                    .collect { loadState ->
//                        Log.d(TAG, "loadState: $loadState")
                        Log.d(TAG, "  adapter.itemCount: ${adapter.itemCount}")
                        Log.d(TAG, "  refresh?: ${loadState.refresh}")
                        Log.d(TAG, "  source.refresh?: ${loadState.source.refresh}")
                        Log.d(TAG, "  mediator.refresh?: ${loadState.mediator?.refresh}")

                        val listIsEmpty = loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0

                        binding.statusView.isVisible = listIsEmpty
                        binding.recyclerView.isVisible = adapter.itemCount != 0 || loadState.source.refresh is LoadState.NotLoading || loadState.mediator?.refresh is LoadState.NotLoading
                        binding.progressBar.isVisible = loadState.mediator?.refresh is LoadState.Loading && listIsEmpty
                        binding.swipeRefreshLayout.isRefreshing = loadState.mediator?.refresh is LoadState.Loading

                        if (listIsEmpty) {
                            binding.statusView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty
                            )
                            if (timelineKind == TimelineKind.Home) {
                                binding.statusView.showHelp(R.string.help_empty_home)
                            }
                            return@collect
                        }

                        if (loadState.mediator?.refresh is LoadState.Error) {
                            val message = (loadState.mediator?.refresh as LoadState.Error).error.getErrorString(requireContext())

                            // Show errors as a snackbar if there is existing content to show
                            // (either cached, or in the adapter), or as a full screen error
                            // otherwise.
                            if (viewModel is CachedTimelineViewModel || adapter.itemCount > 0) {
                                snackbar = Snackbar.make(
                                    (activity as ActionButtonActivity).actionButton ?: binding.root,
                                    message,
                                    Snackbar.LENGTH_INDEFINITE
                                )
                                    .setTextMaxLines(5)
                                    .setAction(R.string.action_retry) { adapter.retry() }
                                snackbar!!.show()
                            } else {
                                val drawableRes = (loadState.refresh as LoadState.Error).error.getDrawableRes()
                                binding.statusView.setup(drawableRes, message) { adapter.retry() }
                                binding.statusView.show()
                            }
                        }
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
                    refreshContent()
                    true
                } else {
                    false
                }
            }
            R.id.action_load_newest -> {
                viewModel.accept(InfallibleUiAction.LoadNewest)
                refreshContent()
                true
            }
            else -> false
        }
    }

    /**
     * Save the ID of the last visible status in the list
     */
    fun saveVisibleId() = layoutManager
        .findLastCompletelyVisibleItemPosition()
        .takeIf { it != RecyclerView.NO_POSITION }
        ?.let { position ->
            adapter.snapshot().getOrNull(position)?.id?.let { statusId ->
                viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = statusId))
            }
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

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = TimelineLoadStateAdapter { adapter.retry() },
            footer = TimelineLoadStateAdapter { adapter.retry() }
        )
    }

    override fun onRefresh() {
        binding.statusView.hide()

        adapter.refresh()
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position) ?: return
        super.reply(status.status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val statusViewData = adapter.peek(position) ?: return
        viewModel.accept(StatusAction.Reblog(reblog, statusViewData))
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val statusViewData = adapter.peek(position) ?: return
        viewModel.accept(StatusAction.Favourite(favourite, statusViewData))
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val statusViewData = adapter.peek(position) ?: return
        viewModel.accept(StatusAction.Bookmark(bookmark, statusViewData))
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val statusViewData = adapter.peek(position) ?: return
        val poll = statusViewData.status.poll ?: return
        viewModel.accept(StatusAction.VoteInPoll(poll, choices, statusViewData))
    }

    override fun clearWarningAction(position: Int) {
        val status = adapter.peek(position) ?: return
        viewModel.clearWarning(status)
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter.peek(position) ?: return
        super.more(status.status, view, position)
    }

    override fun onOpenReblog(position: Int) {
        val status = adapter.peek(position) ?: return
        super.openReblog(status.status)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = adapter.peek(position) ?: return
        viewModel.changeExpanded(expanded, status)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = adapter.peek(position) ?: return
        viewModel.changeContentShowing(isShowing, status)
    }

    override fun onShowReblogs(position: Int) {
        val statusId = adapter.peek(position)?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = adapter.peek(position)?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val status = adapter.peek(position) ?: return
        viewModel.changeContentCollapsed(isCollapsed, status)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.peek(position) ?: return
        super.viewMedia(
            attachmentIndex,
            AttachmentViewData.list(status.actionable),
            view
        )
    }

    override fun onViewThread(position: Int) {
        val status = adapter.peek(position) ?: return
        super.viewThread(status.actionable.id, status.actionable.url)
    }

    override fun onViewTag(tag: String) {
        val timelineKind = viewModel.timelineKind

        // If already viewing a tag page, then ignore any request to view that tag again.
        if (timelineKind is TimelineKind.Tag && timelineKind.tags.contains(tag)) {
            return
        }

        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        val timelineKind = viewModel.timelineKind

        // Ignore request to view the account page we're currently viewing
        if (timelineKind is TimelineKind.User && timelineKind.id == id) {
            return
        }

        super.viewAccount(id)
    }

    /**
     * A status the user has written has either:
     *
     * - Been successfully posted
     * - Been edited by the user
     *
     * Depending on the timeline kind it may need refreshing to show the new status or the changes
     * that have been made to it.
     */
    private fun handleStatusSentOrEdit(status: Status) {
        when (timelineKind) {
            is TimelineKind.User.Pinned -> return

            is TimelineKind.Home,
            is TimelineKind.PublicFederated,
            is TimelineKind.PublicLocal -> adapter.refresh()
            is TimelineKind.User -> if (status.account.id == (timelineKind as TimelineKind.User).id) {
                adapter.refresh()
            }
            is TimelineKind.Bookmarks,
            is TimelineKind.Favourites,
            is TimelineKind.Tag,
            is TimelineKind.UserList -> return
        }
    }

    public override fun removeItem(position: Int) {
        val status = adapter.peek(position) ?: return
        viewModel.removeStatusWithId(status.id)
    }

    private fun actionButtonPresent(): Boolean {
        return viewModel.timelineKind !is TimelineKind.Tag &&
            viewModel.timelineKind !is TimelineKind.Favourites &&
            viewModel.timelineKind !is TimelineKind.Bookmarks &&
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

    override fun onPause() {
        super.onPause()

        saveVisibleId()
        snackbar?.dismiss()
    }

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    companion object {
        private const val TAG = "TimelineFragment" // logging tag
        private const val KIND_ARG = "kind"
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "enableSwipeToRefresh"

        fun newInstance(
            timelineKind: TimelineKind,
            enableSwipeToRefresh: Boolean = true
        ): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(2)
            arguments.putParcelable(KIND_ARG, timelineKind)
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, enableSwipeToRefresh)
            fragment.arguments = arguments
            return fragment
        }
    }
}
