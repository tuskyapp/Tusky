/* Copyright 2017 Andrew Dawson
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
package com.keylesspalace.tusky.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.components.notifications.FallibleUiAction
import com.keylesspalace.tusky.components.notifications.InfallibleUiAction
import com.keylesspalace.tusky.components.notifications.NotificationAction
import com.keylesspalace.tusky.components.notifications.NotificationActionListener
import com.keylesspalace.tusky.components.notifications.NotificationActionSuccess
import com.keylesspalace.tusky.components.notifications.NotificationsLoadStateAdapter
import com.keylesspalace.tusky.components.notifications.NotificationsPagingAdapter
import com.keylesspalace.tusky.components.notifications.NotificationsViewModel
import com.keylesspalace.tusky.components.notifications.StatusAction
import com.keylesspalace.tusky.components.notifications.StatusActionSuccess
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Notification.Type.Companion.asList
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData.Companion.list
import com.keylesspalace.tusky.viewdata.NotificationViewData
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
    Injectable,
    ReselectableFragment {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: NotificationsViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private lateinit var adapter: NotificationsPagingAdapter

    private var layoutManager: LinearLayoutManager? = null
    private var showingError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = NotificationsPagingAdapter(
            notificationDiffCallback,
            accountId = accountManager.activeAccount!!.accountId,
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
        if (showFilter && !showingError) {
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

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener { adapter.refresh() }
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
                val notification = adapter.snapshot()[pos]
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
        })

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = NotificationsLoadStateAdapter { adapter.retry() },
            footer = NotificationsLoadStateAdapter { adapter.retry() }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingData.collectLatest { pagingData ->
                Log.d(TAG, "Submitting data to adapter")
                adapter.submitData(pagingData)
            }
        }

        /**
         * Collect this flow to notify the adapter that the timestamps of the visible items have
         * changed
         */
        val updateTimestampFlow = flow {
            while (true) { delay(60000); emit(Unit) }
        }.onEach {
            layoutManager?.findFirstVisibleItemPosition()?.let { first ->
                first == RecyclerView.NO_POSITION && return@let
                val count = layoutManager!!.findLastVisibleItemPosition() - first
                adapter.notifyItemRangeChanged(
                    first,
                    count,
                    listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Show errors from the view model as snack bars.
                //
                // Errors are shown:
                // - Indefinitely, so the user has a chance to read and understand
                //   the message
                // - With a max of 5 text lines, to allow space for longer errors.
                //   E.g., on a typical device, an error message like "Bookmarking
                //   post failed: Unable to resolve host 'mastodon.social': No
                //   address associated with hostname" is 3 lines.
                this.launch {
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
                            action is StatusAction || return@let

                            // TODO: Finding position by status ID is common enough this
                            // should be a method in the adapter
                            val position = adapter.snapshot()
                                .indexOfFirst {
                                    it?.statusViewData?.status?.id ==
                                        (action as StatusAction).statusViewData.id
                                }
                            if (position != -1) {
                                adapter.notifyItemChanged(position)
                            }
                        }
                    }
                }

                // Show successful notification action as brief snackbars, so the
                // user is clear the action has happened.
                this.launch {
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

                this.launch {
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

                this.launch {
                    viewModel.uiState
                        .collectLatest { uiState ->
                            updateFilterVisibility(uiState.showFilterOptions)
                        }
                }

                this.launch {
                    viewModel.statusDisplayOptions
                        .collectLatest {
                            adapter.statusDisplayOptions = it
                            layoutManager?.findFirstVisibleItemPosition()?.let { first ->
                                first == RecyclerView.NO_POSITION && return@let
                                val count = layoutManager!!.findLastVisibleItemPosition() - first
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

                adapter.loadStateFlow
                    .distinctUntilChangedBy { it.refresh }
                    .collect { loadState ->
                        binding.recyclerView.isVisible = loadState.refresh is LoadState.NotLoading
                        binding.swipeRefreshLayout.isRefreshing =
                            loadState.refresh is LoadState.Loading

                        binding.statusView.isVisible = false
                        if (loadState.refresh is LoadState.NotLoading) {
                            if (adapter.itemCount == 0) {
                                binding.statusView.setup(
                                    R.drawable.elephant_friend_empty,
                                    R.string.message_empty
                                )
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
                            binding.statusView.isVisible = true
                        }
                    }
            }
        }

        binding.buttonClear.setOnClickListener { confirmClearNotifications() }
        binding.buttonFilter.setOnClickListener { showFilterMenu() }
        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false
    }

    override fun onPause() {
        super.onPause()

        // Save the ID of the first notification visible in the list
        val position = layoutManager!!.findFirstVisibleItemPosition()
        if (position >= 0) {
            adapter.snapshot()[position]?.id?.let { id ->
                viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = id))
            }
        }
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

    private fun clearNotifications() {
        binding.swipeRefreshLayout.isRefreshing = false
        viewModel.accept(FallibleUiAction.ClearNotifications)
    }

    private fun showFilterMenu() {
        val notificationsList = asList
        val list: MutableList<String> = ArrayList()
        for (type in notificationsList) {
            list.add(getNotificationText(type))
        }
        val context = requireContext()
        val adapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, list)
        val window = PopupWindow(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.notifications_filter, view as ViewGroup?, false)
        val listView = view.findViewById<ListView>(R.id.listView)
        view.findViewById<View>(R.id.buttonApply)
            .setOnClickListener {
                val checkedItems = listView.checkedItemPositions
                val excludes: MutableSet<Notification.Type> = HashSet()
                for (i in notificationsList.indices) {
                    if (!checkedItems[i, false]) excludes.add(notificationsList[i])
                }
                window.dismiss()
                if (viewModel.uiState.value.activeFilter != excludes) {
                    viewModel.accept(InfallibleUiAction.ApplyFilter(excludes))
                }
            }
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in notificationsList.indices) {
            if (!viewModel.uiState.value.activeFilter.contains(notificationsList[i])) {
                listView.setItemChecked(i, true)
            }
        }
        window.contentView = view
        window.isFocusable = true
        window.width = ViewGroup.LayoutParams.WRAP_CONTENT
        window.height = ViewGroup.LayoutParams.WRAP_CONTENT
        window.showAsDropDown(binding.buttonFilter)
    }

    private fun getNotificationText(type: Notification.Type): String {
        return when (type) {
            Notification.Type.MENTION -> getString(R.string.notification_mention_name)
            Notification.Type.FAVOURITE -> getString(R.string.notification_favourite_name)
            Notification.Type.REBLOG -> getString(R.string.notification_boost_name)
            Notification.Type.FOLLOW -> getString(R.string.notification_follow_name)
            Notification.Type.FOLLOW_REQUEST -> getString(R.string.notification_follow_request_name)
            Notification.Type.POLL -> getString(R.string.notification_poll_name)
            Notification.Type.STATUS -> getString(R.string.notification_subscription_name)
            Notification.Type.SIGN_UP -> getString(R.string.notification_sign_up_name)
            Notification.Type.UPDATE -> getString(R.string.notification_update_name)
            Notification.Type.REPORT -> getString(R.string.notification_report_name)
            else -> "Unknown"
        }
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        // No muting from notifications yet
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        // No blocking from notifications yet
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
            String.format(
                "https://%s/admin/reports/%s",
                accountManager.activeAccount!!.domain,
                reportId
            )
        )
    }

    public override fun removeItem(position: Int) {
        // Empty -- this fragment doesn't remove items
    }

    override fun onReselect() {
        if (isAdded) {
            binding.appBarOptions.setExpanded(true, false)
            layoutManager!!.scrollToPosition(0)
        }
    }

    companion object {
        private const val TAG = "NotificationF"
        fun newInstance(): NotificationsFragment {
            val fragment = NotificationsFragment()
            val arguments = Bundle()
            fragment.arguments = arguments
            return fragment
        }

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
