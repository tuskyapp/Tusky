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
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.sparkbutton.helpers.Utils
import autodispose2.AutoDispose
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.PollVoteEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.notifications.NotificationActionListener
import com.keylesspalace.tusky.components.notifications.NotificationsLoadStateAdapter
import com.keylesspalace.tusky.components.notifications.NotificationsPagingAdapter
import com.keylesspalace.tusky.components.notifications.NotificationsViewModel
import com.keylesspalace.tusky.components.notifications.UiAction
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Notification.Type.Companion.asList
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.PairedList
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import com.keylesspalace.tusky.viewdata.AttachmentViewData.Companion.list
import com.keylesspalace.tusky.viewdata.NotificationViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
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

    @Inject
    lateinit var eventHub: EventHub

    private lateinit var adapter: NotificationsPagingAdapter

    private val notificationFilter: MutableSet<Notification.Type> = HashSet()
    private val disposables = CompositeDisposable()

    /**
     * Placeholder for the notificationsEnabled. Consider moving to the separate class to hide constructor
     * and reuse in different places as needed.
     */
    private class Placeholder private constructor(val id: Long) {
        companion object {
            fun getInstance(id: Long): Placeholder {
                return Placeholder(id)
            }
        }
    }

    private var layoutManager: LinearLayoutManager? = null
    private lateinit var scrollListener: EndlessOnScrollListener
    private var topLoading = false
    private var bottomLoading = false
    private var bottomId: String? = null
    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false
    private var showingError = false

    // Each element is either a Notification for loading data or a Placeholder
    private val notifications =
        PairedList<Either<Placeholder, Notification>, NotificationViewData> { input ->
            if (input.isRight()) {
                val notification = input.asRight()
                    .rewriteToStatusTypeIfNeeded(accountManager.activeAccount!!.accountId)
                val sensitiveStatus =
                    notification.status != null && notification.status.actionableStatus.sensitive
                notification.toViewData(
                    alwaysShowSensitiveMedia || !sensitiveStatus,
                    alwaysOpenSpoiler,
                    true
                )
            } else {
                NotificationViewData.Placeholder(input.asLeft().id, false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = NotificationsPagingAdapter(
            notificationDiffCallback,
            accountId = accountManager.activeAccount!!.accountId,
            statusActionListener = this,
            notificationActionListener = this,
            accountActionListener = this,
            statusDisplayOptions = viewModel.statusDisplayOptions
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
                if (notification is NotificationViewData.Concrete) {
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
        alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = NotificationsLoadStateAdapter { adapter.retry() },
            footer = NotificationsLoadStateAdapter { adapter.retry() }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingDataFlow.collectLatest { pagingData ->
                Log.d(TAG, "Submitting data to adapter")
                adapter.submitData(pagingData)
            }
        }

        /** Notifies the adapter that the timestamps of the visible items have changed */
        val updateTimestampFlow = flow {
            while (true) { delay(60000); emit(Unit) }
        }.onEach {
            layoutManager?.findFirstVisibleItemPosition()?.let { first ->
                val count = layoutManager!!.findLastVisibleItemPosition() - first
                adapter.notifyItemRangeChanged(first, count, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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

                viewModel.uiState.collectLatest { uiState ->
                    updateFilterVisibility(uiState.showFilterOptions)

                    if (!uiState.showAbsoluteTime) {
                        updateTimestampFlow.collect()
                    }
                }
            }
        }

        binding.buttonClear.setOnClickListener { confirmClearNotifications() }
        binding.buttonFilter.setOnClickListener { showFilterMenu() }
        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false

        eventHub.events
            .observeOn(AndroidSchedulers.mainThread())
            .to(
                AutoDispose.autoDisposable(
                    AndroidLifecycleScopeProvider.from(
                        this,
                        Lifecycle.Event.ON_DESTROY
                    )
                )
            )
            .subscribe { event: Event? ->
                when (event) {
                    is FavoriteEvent -> setFavouriteForStatus(event.statusId, event.favourite)
                    is BookmarkEvent -> setBookmarkForStatus(event.statusId, event.bookmark)
                    is ReblogEvent -> setReblogForStatus(event.statusId, event.reblog)
                    is PollVoteEvent -> setVoteForPoll(event.statusId, event.poll)
                    is PinEvent -> setPinForStatus(event.statusId, event.pinned)
                    is BlockEvent -> removeAllByAccountId(event.accountId)
                }
            }
    }

    override fun onPause() {
        super.onPause()

        // Save the ID of the first notification visible in the list
        val position = layoutManager!!.findFirstVisibleItemPosition()
        if (position >= 0) {
            adapter.snapshot()[position]?.id?.let { id ->
                viewModel.accept(UiAction.SaveVisibleId(visibleId = id))
            }
        }
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.reply(status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.reblog(reblog, statusViewData)
    }

    private fun setReblogForStatus(statusId: String, reblogged: Boolean) {
        val indexedViewData = adapter.snapshot().withIndex().firstOrNull { notificationViewData ->
            notificationViewData.value?.statusViewData?.status?.id == statusId
        } ?: return

        val statusViewData = indexedViewData.value?.statusViewData ?: return

        indexedViewData.value?.statusViewData = statusViewData.copy(
            status = statusViewData.status.copy(reblogged = reblogged)
        )

        adapter.notifyItemChanged(indexedViewData.index)
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.favorite(favourite, statusViewData)
    }

    private fun setFavouriteForStatus(statusId: String, favourite: Boolean) {
        val indexedViewData = adapter.snapshot().withIndex().firstOrNull { notificationViewData ->
            notificationViewData.value?.statusViewData?.status?.id == statusId
        } ?: return

        val statusViewData = indexedViewData.value?.statusViewData ?: return

        indexedViewData.value?.statusViewData = statusViewData.copy(
            status = statusViewData.status.copy(favourited = favourite)
        )

        adapter.notifyItemChanged(indexedViewData.index)
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.bookmark(bookmark, statusViewData)
    }

    private fun setBookmarkForStatus(statusId: String, bookmark: Boolean) {
        val indexedViewData = adapter.snapshot().withIndex().firstOrNull { notificationViewData ->
            notificationViewData.value?.statusViewData?.status?.id == statusId
        } ?: return

        val statusViewData = indexedViewData.value?.statusViewData ?: return

        indexedViewData.value?.statusViewData = statusViewData.copy(
            status = statusViewData.status.copy(bookmarked = bookmark)
        )

        adapter.notifyItemChanged(indexedViewData.index)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.voteInPoll(choices, statusViewData)
    }

    private fun setVoteForPoll(statusId: String, poll: Poll) {
        val indexedViewData = adapter.snapshot().withIndex().firstOrNull { notificationViewData ->
            notificationViewData.value?.statusViewData?.status?.id == statusId
        } ?: return

        val statusViewData = indexedViewData.value?.statusViewData ?: return

        indexedViewData.value?.statusViewData = statusViewData.copy(
            status = statusViewData.status.copy(poll = poll)
        )

        adapter.notifyItemChanged(indexedViewData.index)
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
        val (_, _, account) = notifications[position].asRight()
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

    // TODO: Get confirmation on whether this can deleted. The UI doesn't support pinning
    private fun setPinForStatus(statusId: String, pinned: Boolean) {
        // updateStatus(statusId) { status: Status? -> status!!.copyWithPinned(pinned) }
    }

    override fun onLoadMore(position: Int) {
        // Check bounds before accessing list,
        if (notifications.size >= position && position > 0) {
            val previous = notifications[position - 1].asRightOrNull()
            val next = notifications[position + 1].asRightOrNull()
            if (previous == null || next == null) {
                Log.e(TAG, "Failed to load more, invalid placeholder position: $position")
                return
            }
            // sendFetchNotificationsRequest(previous.id, next.id, FetchEnd.MIDDLE, position)
            val placeholder = notifications[position].asLeft()
            val notificationViewData: NotificationViewData =
                NotificationViewData.Placeholder(placeholder.id, true)
            notifications.setPairedItem(position, notificationViewData)
            updateAdapter()
        } else {
            Log.d(TAG, "error loading more")
        }
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
        // Cancel all ongoing requests
        binding.swipeRefreshLayout.isRefreshing = false
        resetNotificationsLoad()

        // Show friend elephant
//        binding.statusView.visibility = View.VISIBLE
        // TODO: Use this when the list is empty
//        binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty)
        updateFilterVisibility(viewModel.uiState.value.showFilterOptions)

        // Update adapter
        updateAdapter()

        // Execute clear notifications request
        mastodonApi.clearNotifications()
            .observeOn(AndroidSchedulers.mainThread())
            .to(
                AutoDispose.autoDisposable(
                    AndroidLifecycleScopeProvider.from(
                        this,
                        Lifecycle.Event.ON_DESTROY
                    )
                )
            )
            .subscribe(
                { }
            ) {
                // Reload notifications on failure
                adapter.refresh()
            }
    }

    private fun resetNotificationsLoad() {
        disposables.clear()
        bottomLoading = false
        topLoading = false

        // Disable load more
        bottomId = null

        // Clear exists notifications
        notifications.clear()
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
                    viewModel.accept(UiAction.ApplyFilter(excludes))
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

    override fun onRespondToFollowRequest(accept: Boolean, id: String, position: Int) {
        val request =
            if (accept) mastodonApi.authorizeFollowRequest(id) else mastodonApi.rejectFollowRequest(
                id
            )
        request.observeOn(AndroidSchedulers.mainThread())
            .to(
                AutoDispose.autoDisposable(
                    AndroidLifecycleScopeProvider.from(
                        this,
                        Lifecycle.Event.ON_DESTROY
                    )
                )
            )
            .subscribe(
                { adapter.refresh() }
            ) {
                Log.e(
                    TAG,
                    String.format(
                        "Failed to %s account id %s",
                        if (accept) "accept" else "reject",
                        id
                    ),
                    it
                )
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

//    private fun onPreferenceChanged(key: String) {
//        when (key) {
//            "fabHide" -> {
//                hideFab = PreferenceManager.getDefaultSharedPreferences(requireContext())
//                    .getBoolean("fabHide", false)
//            }
//            "mediaPreviewEnabled" -> {
//                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
////                if (enabled != adapter.isMediaPreviewEnabled) {
////                    adapter.isMediaPreviewEnabled = enabled
////                    fullyRefresh()
////                }
//            }
//            "showNotificationsFilter" -> {
//                if (isAdded) {
//                    showNotificationsFilter =
//                        PreferenceManager.getDefaultSharedPreferences(requireContext())
//                            .getBoolean("showNotificationsFilter", true)
//                    updateFilterVisibility()
//                    fullyRefreshWithProgressBar(true)
//                }
//            }
//        }
//    }

    public override fun removeItem(position: Int) {
        notifications.removeAt(position)
        updateAdapter()
    }

    private fun removeAllByAccountId(accountId: String) {
        // Using iterator to safely remove items while iterating
        val iterator = notifications.iterator()
        while (iterator.hasNext()) {
            val notification = iterator.next()
            val maybeNotification = notification.asRightOrNull()
            if (maybeNotification != null && maybeNotification.account.id == accountId) {
                iterator.remove()
            }
        }
        updateAdapter()
    }

    private fun jumpToTop() {
        if (isAdded) {
            binding.appBarOptions.setExpanded(true, false)
            layoutManager!!.scrollToPosition(0)
            scrollListener.reset()
        }
    }

    private fun updateAdapter() {
        differ.submitList(adapter.snapshot())
    }

    private val listUpdateCallback: ListUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (isAdded) {
                adapter.notifyItemRangeInserted(position, count)
                val context = context
                // scroll up when new items at the top are loaded while being at the start
                // https://github.com/tuskyapp/Tusky/pull/1905#issuecomment-677819724
                if (position == 0 && context != null && adapter.itemCount != count) {
                    binding.recyclerView.scrollBy(0, Utils.dpToPx(context, -30))
                }
            }
        }

        override fun onRemoved(position: Int, count: Int) {
            adapter.notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            adapter.notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            adapter.notifyItemRangeChanged(position, count, payload)
        }
    }
    private val differ = AsyncListDiffer(
        listUpdateCallback,
        AsyncDifferConfig.Builder(notificationDiffCallback).build()
    )

    override fun onReselect() {
        jumpToTop()
    }

    companion object {
        private const val TAG = "NotificationF" // logging tag
        fun newInstance(): NotificationsFragment {
            val fragment = NotificationsFragment()
            val arguments = Bundle()
            fragment.arguments = arguments
            return fragment
        }

        private val notificationDiffCallback: DiffUtil.ItemCallback<NotificationViewData.Concrete> =
            object : DiffUtil.ItemCallback<NotificationViewData.Concrete>() {
                override fun areItemsTheSame(
                    oldItem: NotificationViewData.Concrete,
                    newItem: NotificationViewData.Concrete
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: NotificationViewData.Concrete,
                    newItem: NotificationViewData.Concrete
                ): Boolean {
                    return false
                }

                override fun getChangePayload(
                    oldItem: NotificationViewData.Concrete,
                    newItem: NotificationViewData.Concrete
                ): Any? {
                    return if (oldItem.deepEquals(newItem)) {
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
