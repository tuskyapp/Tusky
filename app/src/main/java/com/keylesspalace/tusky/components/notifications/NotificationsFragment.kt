/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.notifications

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData.Companion.list
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class NotificationsFragment :
    SFragment(),
    StatusActionListener,
    NotificationActionListener,
    AccountActionListener,
    OnRefreshListener,
    MenuProvider,
    Injectable,
    ReselectableFragment {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: NotificationsViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private lateinit var adapter: NotificationsPagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = NotificationsPagingAdapter(
            notificationDiffCallback,
            accountId = viewModel.account.accountId,
            statusActionListener = this,
            notificationActionListener = this,
            accountActionListener = this,
            statusDisplayOptions = viewModel.statusDisplayOptions.value
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_timeline_notifications, container, false)
    }

    private fun updateFilterVisibility(showFilter: Boolean) {
        val params = binding.swipeRefreshLayout.layoutParams as CoordinatorLayout.LayoutParams
        if (showFilter) {
            binding.appBarOptions.setExpanded(true, false)
            binding.appBarOptions.visibility = View.VISIBLE
            // Set content behaviour to hide filter on scroll
            params.behavior = ScrollingViewBehavior()
        } else {
            binding.appBarOptions.setExpanded(false, false)
            binding.appBarOptions.visibility = View.GONE
            // Clear behaviour to hide app bar
            params.behavior = null
        }
        // Set name of "Swap" button
        // FIXME: This politely skips if filters has not yet been populated, BUT if filters or filterIndex ever get set to "strange" values it could impolitely crash.
        if (viewModel.uiState.value.filters.size > 0) {
            val offset =
                viewModel.uiState.value.filters[viewModel.uiState.value.filterIndex].size - viewModel.uiState.value.filters[1 - viewModel.uiState.value.filterIndex].size
            val swapText = if (offset < 0) {
                R.string.notifications_swap_less
            } else if (offset > 0) {
                R.string.notifications_swap_more
            } else {
                R.string.notifications_swap
            }
            binding.buttonSwap.setText(swapText)
        }
    }

    private fun confirmClearNotifications() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.notification_clear_text)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> clearNotifications() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        // Setup the RecyclerView.
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this
            ) { pos: Int ->
                val notification = adapter.snapshot().getOrNull(pos)
                // We support replies only for now
                if (notification is NotificationViewData) {
                    notification.statusViewData
                } else {
                    null
                }
            }
        )
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            val actionButton = (activity as ActionButtonActivity).actionButton

            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                actionButton?.let { fab ->
                    if (!viewModel.uiState.value.showFabWhileScrolling) {
                        if (dy > 0 && fab.isShown) {
                            fab.hide() // Hide when scrolling down
                        } else if (dy < 0 && !fab.isShown) {
                            fab.show() // Show when scrolling up
                        }
                    } else if (!fab.isShown) {
                        fab.show()
                    }
                }
            }

            @Suppress("SyntheticAccessor")
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                newState != SCROLL_STATE_IDLE && return

                // Save the ID of the first notification visible in the list, so the user's
                // reading position is always restorable.
                layoutManager.findFirstVisibleItemPosition().takeIf { it != NO_POSITION }?.let { position ->
                    adapter.snapshot().getOrNull(position)?.id?.let { id ->
                        viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = id))
                    }
                }
            }
        })

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = NotificationsLoadStateAdapter { adapter.retry() },
            footer = NotificationsLoadStateAdapter { adapter.retry() }
        )

        // binding.buttonClear.setOnClickListener { confirmClearNotifications() }
        binding.buttonSwap.setOnClickListener { swapNotifications() }
        binding.buttonFilter.setOnClickListener { showFilterDialog() }
        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false

        // Signal the user that a refresh has loaded new items above their current position
        // by scrolling up slightly to disclose the new content
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            binding.recyclerView.scrollBy(0, Utils.dpToPx(requireContext(), -30))
                        }
                    }
                }
            }
        })

        // update post timestamps
        val updateTimestampFlow = flow {
            while (true) {
                delay(60000)
                emit(Unit)
            }
        }.onEach {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagingData.collectLatest { pagingData ->
                        Log.d(TAG, "Submitting data to adapter")
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
                launch {
                    viewModel.uiError.collect { error ->
                        Log.d(TAG, error.toString())
                        val message = getString(
                            error.message,
                            error.throwable.localizedMessage
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
                            action is StatusAction || return@let

                            val position = adapter.snapshot().indexOfFirst {
                                it?.statusViewData?.status?.id == (action as StatusAction).statusViewData.id
                            }
                            if (position != RecyclerView.NO_POSITION) {
                                adapter.notifyItemChanged(position)
                            }
                        }
                    }
                }

                // Show successful notification action as brief snackbars, so the
                // user is clear the action has happened.
                launch {
                    viewModel.uiSuccess
                        .filterIsInstance<NotificationActionSuccess>()
                        .collect {
                            Snackbar.make(
                                (activity as ActionButtonActivity).actionButton ?: binding.root,
                                getString(it.msg),
                                Snackbar.LENGTH_SHORT
                            ).show()

                            when (it) {
                                // The follow request is no longer valid, refresh the adapter to
                                // remove it.
                                is NotificationActionSuccess.AcceptFollowRequest,
                                is NotificationActionSuccess.RejectFollowRequest -> adapter.refresh()
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
                                .firstOrNull { notificationViewData ->
                                    notificationViewData.value?.statusViewData?.status?.id ==
                                        it.action.statusViewData.id
                                } ?: return@collect

                            val statusViewData =
                                indexedViewData.value?.statusViewData ?: return@collect

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
                            indexedViewData.value?.statusViewData = statusViewData.copy(
                                status = status
                            )

                            adapter.notifyItemChanged(indexedViewData.index)
                        }
                }

                // Refresh adapter on mutes and blocks
                launch {
                    viewModel.uiSuccess.collectLatest {
                        when (it) {
                            is UiSuccess.Block, is UiSuccess.Mute, is UiSuccess.MuteConversation ->
                                adapter.refresh()
                            else -> { /* nothing to do */
                            }
                        }
                    }
                }

                // Update filter option visibility from uiState
                launch {
                    viewModel.uiState.collectLatest { updateFilterVisibility(it.showFilterOptions) }
                }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically update the timestamp in the list gui elements.
                launch {
                    viewModel.statusDisplayOptions
                        .collectLatest {
                            // NOTE this this also triggered (emitted?) on resume.

                            adapter.statusDisplayOptions = it
                            adapter.notifyItemRangeChanged(0, adapter.itemCount, null)

                            if (!it.useAbsoluteTime) {
                                updateTimestampFlow.collect()
                            }
                        }
                }

                // Update the UI from the loadState
                adapter.loadStateFlow
                    .distinctUntilChangedBy { it.refresh }
                    .collect { loadState ->
                        binding.recyclerView.isVisible = true
                        binding.progressBar.isVisible = loadState.refresh is LoadState.Loading &&
                            !binding.swipeRefreshLayout.isRefreshing
                        binding.swipeRefreshLayout.isRefreshing =
                            loadState.refresh is LoadState.Loading && !binding.progressBar.isVisible

                        binding.statusView.isVisible = false
                        if (loadState.refresh is LoadState.NotLoading) {
                            if (adapter.itemCount == 0) {
                                binding.statusView.setup(
                                    R.drawable.elephant_friend_empty,
                                    R.string.message_empty
                                )
                                binding.recyclerView.isVisible = false
                                binding.statusView.isVisible = true
                            } else {
                                binding.statusView.isVisible = false
                            }
                        }

                        if (loadState.refresh is LoadState.Error) {
                            when ((loadState.refresh as LoadState.Error).error) {
                                is IOException -> {
                                    binding.statusView.setup(
                                        R.drawable.elephant_offline,
                                        R.string.error_network
                                    ) { adapter.retry() }
                                }
                                else -> {
                                    binding.statusView.setup(
                                        R.drawable.elephant_error,
                                        R.string.error_generic
                                    ) { adapter.retry() }
                                }
                            }
                            binding.recyclerView.isVisible = false
                            binding.statusView.isVisible = true
                        }
                    }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_notifications, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                true
            }
            R.id.load_newest -> {
                viewModel.accept(InfallibleUiAction.LoadNewest)
                true
            }
            else -> false
        }
    }

    override fun onRefresh() {
        binding.progressBar.isVisible = false
        adapter.refresh()
        NotificationHelper.clearNotificationsForAccount(requireContext(), viewModel.account)
    }

    override fun onPause() {
        super.onPause()

        // Save the ID of the first notification visible in the list
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position >= 0) {
            adapter.snapshot().getOrNull(position)?.id?.let { id ->
                viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = id))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationHelper.clearNotificationsForAccount(requireContext(), viewModel.account)
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.reply(status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.accept(StatusAction.Reblog(reblog, statusViewData))
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.accept(StatusAction.Favourite(favourite, statusViewData))
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.accept(StatusAction.Bookmark(bookmark, statusViewData))
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        val poll = statusViewData.status.poll ?: return
        viewModel.accept(StatusAction.VoteInPoll(poll, choices, statusViewData))
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.more(status, view, position)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.viewMedia(attachmentIndex, list(status), view)
    }

    override fun onViewThread(position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onOpenReblog(position: Int) {
        val account = adapter.peek(position)?.account!!
        onViewAccount(account.id)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            isExpanded = expanded
        )
        adapter.notifyItemChanged(position)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            isShowingContent = isShowing
        )
        adapter.notifyItemChanged(position)
    }

    override fun onLoadMore(position: Int) {
        // Empty -- this fragment doesn't show placeholders
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            isCollapsed = isCollapsed
        )
        adapter.notifyItemChanged(position)
    }

    override fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        onContentCollapsedChange(isCollapsed, position)
    }

    override fun clearWarningAction(position: Int) {
    }

    private fun clearNotifications() {
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.isVisible = false
        viewModel.accept(FallibleUiAction.ClearNotifications)
    }

    private fun swapNotifications() {
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.isVisible = false
        viewModel.accept(InfallibleUiAction.ActiveFilter(1 - viewModel.uiState.value.filterIndex))
    }

    private fun showFilterDialog() {
        FilterDialogFragment(viewModel.uiState.value.activeFilter) { filter ->
            if (viewModel.uiState.value.activeFilter != filter) {
                val filters = viewModel.uiState.value.filters.copyOf()
                filters[viewModel.uiState.value.filterIndex] = filter
                viewModel.accept(InfallibleUiAction.ApplyFilters(filters))
            }
        }
            .show(parentFragmentManager, "dialogFilter")
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        adapter.refresh()
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        adapter.refresh()
    }

    override fun onRespondToFollowRequest(accept: Boolean, accountId: String, position: Int) {
        if (accept) {
            viewModel.accept(NotificationAction.AcceptFollowRequest(accountId))
        } else {
            viewModel.accept(NotificationAction.RejectFollowRequest(accountId))
        }
    }

    override fun onViewThreadForStatus(status: Status) {
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onViewReport(reportId: String) {
        requireContext().openLink(
            "https://${viewModel.account.domain}/admin/reports/$reportId"
        )
    }

    public override fun removeItem(position: Int) {
        // Empty -- this fragment doesn't remove items
    }

    override fun onReselect() {
        if (isAdded) {
            binding.appBarOptions.setExpanded(true, false)
            layoutManager.scrollToPosition(0)
        }
    }

    companion object {
        private const val TAG = "NotificationsFragment"
        fun newInstance() = NotificationsFragment()

        private val notificationDiffCallback: DiffUtil.ItemCallback<NotificationViewData> =
            object : DiffUtil.ItemCallback<NotificationViewData>() {
                override fun areItemsTheSame(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData
                ): Boolean {
                    return false
                }

                override fun getChangePayload(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData
                ): Any? {
                    return if (oldItem == newItem) {
                        //  If items are equal - update timestamp only
                        listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                    } else {
                        // If items are different - update a whole view holder
                        null
                    }
                }
            }
    }
}

class FilterDialogFragment(
    private val activeFilter: Set<Notification.Type>,
    private val listener: ((filter: Set<Notification.Type>) -> Unit)
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val items = Notification.Type.visibleTypes.map { getString(it.uiString) }.toTypedArray()
        val checkedItems = Notification.Type.visibleTypes.map {
            !activeFilter.contains(it)
        }.toBooleanArray()

        val builder = AlertDialog.Builder(context)
            .setTitle(R.string.notifications_apply_filter)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val excludes: MutableSet<Notification.Type> = HashSet()
                for (i in Notification.Type.visibleTypes.indices) {
                    if (!checkedItems[i]) excludes.add(Notification.Type.visibleTypes[i])
                }
                listener(excludes)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
        return builder.create()
    }
}
