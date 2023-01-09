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
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.arch.core.util.Function
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import autodispose2.AutoDispose
import autodispose2.SingleSubscribeProxy
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.NotificationsAdapter
import com.keylesspalace.tusky.adapter.NotificationsAdapter.AdapterDataSource
import com.keylesspalace.tusky.adapter.NotificationsAdapter.NotificationActionListener
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Notification.Type.Companion.asList
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.Either.Left
import com.keylesspalace.tusky.util.Either.Right
import com.keylesspalace.tusky.util.HttpHeaderLink.Companion.findByRelationType
import com.keylesspalace.tusky.util.HttpHeaderLink.Companion.parse
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.PairedList
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.deserialize
import com.keylesspalace.tusky.util.isEmpty
import com.keylesspalace.tusky.util.isLessThan
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.serialize
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import com.keylesspalace.tusky.viewdata.AttachmentViewData.Companion.list
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NotificationsFragment : SFragment(), OnRefreshListener, StatusActionListener,
    NotificationActionListener, AccountActionListener, Injectable, ReselectableFragment {

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private lateinit var adapter: NotificationsAdapter

    private lateinit var preferences: SharedPreferences

    private var maxPlaceholderId = 0
    private val notificationFilter: MutableSet<Notification.Type> = HashSet()
    private val disposables = CompositeDisposable()

    private enum class FetchEnd {
        TOP, BOTTOM, MIDDLE
    }

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
    private var hideFab = false
    private var topLoading = false
    private var bottomLoading = false
    private var bottomId: String? = null
    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false
    private var showNotificationsFilter = false
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

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val statusDisplayOptions = StatusDisplayOptions(
            preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            accountManager.activeAccount!!.mediaPreviewEnabled,
            preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            CardViewMode.NONE,
            preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )

        adapter = NotificationsAdapter(
            accountManager.activeAccount!!.accountId,
            dataSource, statusDisplayOptions, this, this, this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_timeline_notifications, container, false)
    }

    private fun updateFilterVisibility() {
        val params = binding.swipeRefreshLayout.layoutParams as CoordinatorLayout.LayoutParams
        if (showNotificationsFilter && !showingError) {
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

        val showNotificationsFilterSetting = preferences.getBoolean(
            PrefKeys.SHOW_NOTIFICATIONS_FILTER,
            true
        )
        // Clear notifications on filter visibility change to force refresh
        if (showNotificationsFilterSetting != showNotificationsFilter) notifications.clear()
        showNotificationsFilter = showNotificationsFilterSetting

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
        loadNotificationsFilter()

        // Setup the RecyclerView.
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this
            ) { pos: Int ->
                val notification = notifications.getPairedItemOrNull(pos)
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
        binding.recyclerView.adapter = adapter
        topLoading = false
        bottomLoading = false
        bottomId = null
        updateAdapter()
        binding.buttonClear.setOnClickListener { confirmClearNotifications() }
        binding.buttonFilter.setOnClickListener { showFilterMenu() }
        if (notifications.isEmpty()) {
            binding.swipeRefreshLayout.isEnabled = false
            sendFetchNotificationsRequest(null, null, FetchEnd.BOTTOM, -1)
        } else {
            binding.progressBar.visibility = View.GONE
        }
        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false
        updateFilterVisibility()

        hideFab = preferences.getBoolean(PrefKeys.FAB_HIDE, false)
        scrollListener = object : EndlessOnScrollListener(layoutManager) {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(view, dx, dy)
                val actionButton = (requireActivity() as ActionButtonActivity).actionButton
                actionButton?.let { composeButton ->
                    if (hideFab) {
                        if (dy > 0 && composeButton.isShown) {
                            composeButton.hide() // Hides the button if we're scrolling down
                        } else if (dy < 0 && !composeButton.isShown) {
                            composeButton.show() // Shows it if we are scrolling up
                        }
                    } else if (!composeButton.isShown) {
                        composeButton.show()
                    }
                }
            }

            override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                this@NotificationsFragment.onLoadMore()
            }
        }
        binding.recyclerView.addOnScrollListener(scrollListener)

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
                    is PinEvent -> setPinForStatus(event.statusId, event.pinned)
                    is BlockEvent -> removeAllByAccountId(event.accountId)
                    is PreferenceChangedEvent -> onPreferenceChanged(event.preferenceKey)
                }
            }
    }

    override fun onRefresh() {
        binding.statusView.visibility = View.GONE
        showingError = false
        val first = notifications.firstOrNull()
        val topId = first?.asRightOrNull()?.id
        sendFetchNotificationsRequest(null, topId, FetchEnd.TOP, -1)
    }

    override fun onReply(position: Int) {
        super.reply(notifications[position].asRight().status!!)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val (_, _, _, status) = notifications[position].asRight()
        Objects.requireNonNull(status, "Reblog on notification without status")
        timelineCases.reblog(status!!.id, reblog)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                {
                    setReblogForStatus(
                        status.id, reblog
                    )
                }
            ) { t: Throwable? ->
                Log.d(
                    javaClass.simpleName,
                    "Failed to reblog status: " + status.id, t
                )
            }
    }

    private fun setReblogForStatus(statusId: String, reblog: Boolean) {
        updateStatus(statusId) { s: Status? -> s!!.copyWithReblogged(reblog) }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val (_, _, _, status) = notifications[position].asRight()
        timelineCases.favourite(status!!.id, favourite)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                {
                    setFavouriteForStatus(
                        status.id, favourite
                    )
                }
            ) { t: Throwable? ->
                Log.d(
                    javaClass.simpleName,
                    "Failed to favourite status: " + status.id, t
                )
            }
    }

    private fun setFavouriteForStatus(statusId: String, favourite: Boolean) {
        updateStatus(statusId) { s: Status? -> s!!.copyWithFavourited(favourite) }
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val (_, _, _, status) = notifications[position].asRight()
        timelineCases.bookmark(status!!.actionableId, bookmark)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                {
                    setBookmarkForStatus(
                        status.id, bookmark
                    )
                }
            ) { t: Throwable? ->
                Log.d(
                    javaClass.simpleName,
                    "Failed to bookmark status: " + status.id, t
                )
            }
    }

    private fun setBookmarkForStatus(statusId: String, bookmark: Boolean) {
        updateStatus(statusId) { s: Status? -> s!!.copyWithBookmarked(bookmark) }
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val (_, _, _, status1) = notifications[position].asRight()
        val status = status1!!.actionableStatus
        timelineCases.voteInPoll(status.id, status.poll!!.id, choices)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                { newPoll: Poll -> setVoteForPoll(status, newPoll) }
            ) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to vote in poll: " + status.id, t
                )
            }
    }

    private fun setVoteForPoll(status: Status, poll: Poll) {
        updateStatus(status.id) { s: Status? -> s!!.copyWithPoll(poll) }
    }

    override fun onMore(view: View, position: Int) {
        val (_, _, _, status) = notifications[position].asRight()
        super.more(status!!, view, position)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val notification = notifications[position].asRightOrNull()
        if (notification?.status == null) return
        val status = notification.status
        super.viewMedia(attachmentIndex, list(status), view)
    }

    override fun onViewThread(position: Int) {
        val (_, _, _, status1) = notifications[position].asRight()
        val status = status1 ?: return
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onOpenReblog(position: Int) {
        val (_, _, account) = notifications[position].asRight()
        onViewAccount(account.id)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        updateViewDataAt(position) { vd: StatusViewData.Concrete -> vd.copyWithExpanded(expanded) }
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        updateViewDataAt(position) { vd: StatusViewData.Concrete ->
            vd.copyWithShowingContent(
                isShowing
            )
        }
    }

    private fun setPinForStatus(statusId: String, pinned: Boolean) {
        updateStatus(statusId) { status: Status? -> status!!.copyWithPinned(pinned) }
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
            sendFetchNotificationsRequest(previous.id, next.id, FetchEnd.MIDDLE, position)
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
        updateViewDataAt(position) { vd: StatusViewData.Concrete -> vd.copyWithCollapsed(isCollapsed) }
    }

    private fun updateStatus(statusId: String, mapper: Function<Status?, Status>) {
        val index =
            notifications.indexOfFirst { s: Either<Placeholder?, Notification> -> s.asRightOrNull()?.status?.id == statusId }
        if (index == -1) return

        // We have quite some graph here:
        //
        //      Notification --------> Status
        //                                ^
        //                                |
        //                             StatusViewData
        //                                ^
        //                                |
        //      NotificationViewData -----+
        //
        // So if we have "new" status we need to update all references to be sure that data is
        // up-to-date:
        // 1. update status
        // 2. update notification
        // 3. update statusViewData
        // 4. update notificationViewData
        val oldStatus = notifications[index].asRight().status
        val oldViewData = notifications.getPairedItem(index) as NotificationViewData.Concrete
        val newStatus = mapper.apply(oldStatus)
        val newNotification = notifications[index].asRight()
            .copyWithStatus(newStatus)
        val newStatusViewData = oldViewData.statusViewData!!.copyWithStatus(newStatus)
        val newViewData = oldViewData.copyWithStatus(newStatusViewData)
        notifications[index] = Right(newNotification)
        notifications.setPairedItem(index, newViewData)
        updateAdapter()
    }

    private fun updateViewDataAt(
        position: Int,
        mapper: Function<StatusViewData.Concrete, StatusViewData.Concrete>
    ) {
        if (position < 0 || position >= notifications.size) {
            val message = String.format(
                Locale.getDefault(),
                "Tried to access out of bounds status position: %d of %d",
                position,
                notifications.lastIndex
            )
            Log.e(TAG, message)
            return
        }
        val someViewData =
            notifications.getPairedItem(position) as? NotificationViewData.Concrete ?: return
        val oldStatusViewData = someViewData.statusViewData ?: return
        val newViewData = someViewData.copyWithStatus(mapper.apply(oldStatusViewData))
        notifications.setPairedItem(position, newViewData)
        updateAdapter()
    }

    override fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        onContentCollapsedChange(isCollapsed, position)
    }

    private fun clearNotifications() {
        // Cancel all ongoing requests
        binding.swipeRefreshLayout.isRefreshing = false
        resetNotificationsLoad()

        // Show friend elephant
        binding.statusView.visibility = View.VISIBLE
        binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null)
        updateFilterVisibility()

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
                fullyRefreshWithProgressBar(true)
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
                applyFilterChanges(excludes)
            }
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in notificationsList.indices) {
            if (!notificationFilter.contains(notificationsList[i])) listView.setItemChecked(i, true)
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

    private fun applyFilterChanges(newSet: Set<Notification.Type>) {
        val notifications = asList
        var isChanged = false
        for (type in notifications) {
            if (notificationFilter.contains(type) && !newSet.contains(type)) {
                notificationFilter.remove(type)
                isChanged = true
            } else if (!notificationFilter.contains(type) && newSet.contains(type)) {
                notificationFilter.add(type)
                isChanged = true
            }
        }
        if (isChanged) {
            saveNotificationsFilter()
            fullyRefreshWithProgressBar(true)
        }
    }

    private fun loadNotificationsFilter() {
        val account = accountManager.activeAccount
        if (account != null) {
            notificationFilter.clear()
            notificationFilter.addAll(
                deserialize(
                    account.notificationsFilter
                )
            )
        }
    }

    private fun saveNotificationsFilter() {
        val account = accountManager.activeAccount
        if (account != null) {
            account.notificationsFilter = serialize(notificationFilter)
            accountManager.saveAccount(account)
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
                { fullyRefreshWithProgressBar(true) }
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

    override fun onViewStatusForNotificationId(notificationId: String) {
        for (either in notifications) {
            val notification = either.asRightOrNull()
            if (notification != null && notification.id == notificationId) {
                val status = notification.status
                if (status != null) {
                    super.viewThread(status.actionableId, status.actionableStatus.url)
                    return
                }
            }
        }
        Log.w(TAG, "Didn't find a notification for ID: $notificationId")
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

    private fun onPreferenceChanged(key: String) {
        when (key) {
            "fabHide" -> {
                hideFab = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("fabHide", false)
            }
            "mediaPreviewEnabled" -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                if (enabled != adapter.isMediaPreviewEnabled) {
                    adapter.isMediaPreviewEnabled = enabled
                    fullyRefresh()
                }
            }
            "showNotificationsFilter" -> {
                if (isAdded) {
                    showNotificationsFilter =
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .getBoolean("showNotificationsFilter", true)
                    updateFilterVisibility()
                    fullyRefreshWithProgressBar(true)
                }
            }
        }
    }

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

    private fun onLoadMore() {
        if (bottomId == null) {
            // Already loaded everything
            return
        }

        // Check for out-of-bounds when loading
        // This is required to allow full-timeline reloads of collapsible statuses when the settings
        // change.
        if (notifications.size > 0) {
            if (notifications.last().isRight()) {
                val placeholder = newPlaceholder()
                notifications.add(Left(placeholder))
                val viewData: NotificationViewData =
                    NotificationViewData.Placeholder(placeholder.id, true)
                notifications.setPairedItem(notifications.lastIndex, viewData)
                updateAdapter()
            }
        }
        sendFetchNotificationsRequest(bottomId, null, FetchEnd.BOTTOM, -1)
    }

    private fun newPlaceholder(): Placeholder {
        val placeholder = Placeholder.getInstance(maxPlaceholderId.toLong())
        maxPlaceholderId--
        return placeholder
    }

    private fun jumpToTop() {
        if (isAdded) {
            binding.appBarOptions.setExpanded(true, false)
            layoutManager!!.scrollToPosition(0)
            scrollListener.reset()
        }
    }

    private fun sendFetchNotificationsRequest(
        fromId: String?, uptoId: String?,
        fetchEnd: FetchEnd, pos: Int
    ) {
        // If there is a fetch already ongoing, record however many fetches are requested and
        // fulfill them after it's complete.
        if (fetchEnd == FetchEnd.TOP && topLoading) {
            return
        }
        if (fetchEnd == FetchEnd.BOTTOM && bottomLoading) {
            return
        }
        if (fetchEnd == FetchEnd.TOP) {
            topLoading = true
        }
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = true
        }
        val notificationCall = mastodonApi.notifications(
            fromId,
            uptoId,
            LOAD_AT_ONCE,
            if (showNotificationsFilter) notificationFilter else null
        )
            .observeOn(AndroidSchedulers.mainThread())
            .to<SingleSubscribeProxy<Response<List<Notification>>>>(
                AutoDispose.autoDisposable(
                    AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)
                )
            )
            .subscribe(
                { response: Response<List<Notification>> ->
                    if (response.isSuccessful) {
                        val linkHeader = response.headers()["Link"]
                        onFetchNotificationsSuccess(response.body()!!, linkHeader, fetchEnd, pos)
                    } else {
                        onFetchNotificationsFailure(Exception(response.message()), fetchEnd, pos)
                    }
                }
            ) { throwable: Throwable -> onFetchNotificationsFailure(throwable, fetchEnd, pos) }
        disposables.add(notificationCall)
    }

    private fun onFetchNotificationsSuccess(
        notifications: List<Notification>, linkHeader: String?,
        fetchEnd: FetchEnd, pos: Int
    ) {
        val links = parse(linkHeader)
        val next = findByRelationType(links, "next")
        var fromId: String? = null
        if (next != null) {
            fromId = next.uri.getQueryParameter("max_id")
        }
        when (fetchEnd) {
            FetchEnd.TOP -> {
                update(notifications, if (this.notifications.isEmpty()) fromId else null)
            }
            FetchEnd.MIDDLE -> {
                replacePlaceholderWithNotifications(notifications, pos)
            }
            FetchEnd.BOTTOM -> {
                if (this.notifications.isNotEmpty() && this.notifications.last().isLeft()
                ) {
                    this.notifications.removeLast()
                    updateAdapter()
                }
                if (adapter.itemCount > 1) {
                    addItems(notifications, fromId)
                } else {
                    update(notifications, fromId)
                }
            }
        }
        saveNewestNotificationId(notifications)
        if (fetchEnd == FetchEnd.TOP) {
            topLoading = false
        }
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false
        }
        if (notifications.size == 0 && adapter.itemCount == 0) {
            binding.statusView.visibility = View.VISIBLE
            binding.statusView.setup(
                R.drawable.elephant_friend_empty,
                R.string.message_empty,
                null
            )
        }
        updateFilterVisibility()
        binding.swipeRefreshLayout.isEnabled = true
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.visibility = View.GONE
    }

    private fun onFetchNotificationsFailure(
        throwable: Throwable,
        fetchEnd: FetchEnd,
        position: Int
    ) {
        binding.swipeRefreshLayout.isRefreshing = false
        if (fetchEnd == FetchEnd.MIDDLE && notifications[position].isLeft()) {
            val placeholder = notifications[position].asLeft()
            val placeholderVD: NotificationViewData =
                NotificationViewData.Placeholder(placeholder.id, false)
            notifications.setPairedItem(position, placeholderVD)
            updateAdapter()
        } else if (notifications.isEmpty()) {
            binding.statusView.visibility = View.VISIBLE
            binding.swipeRefreshLayout.isEnabled = false
            showingError = true
            if (throwable is IOException) {
                binding.statusView.setup(
                    R.drawable.elephant_offline,
                    R.string.error_network
                ) {
                    binding.progressBar.visibility = View.VISIBLE
                    onRefresh()
                }
            } else {
                binding.statusView.setup(
                    R.drawable.elephant_error,
                    R.string.error_generic
                ) {
                    binding.progressBar.visibility = View.VISIBLE
                    onRefresh()
                }
            }
            updateFilterVisibility()
        }
        Log.e(TAG, "Fetch failure: " + throwable.message)
        if (fetchEnd == FetchEnd.TOP) {
            topLoading = false
        }
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false
        }
        binding.progressBar.visibility = View.GONE
    }

    private fun saveNewestNotificationId(notifications: List<Notification?>) {
        val account = accountManager.activeAccount
        if (account != null) {
            var lastNotificationId = account.lastNotificationId
            for (noti in notifications) {
                if (lastNotificationId.isLessThan(noti!!.id)) {
                    lastNotificationId = noti.id
                }
            }
            if (account.lastNotificationId != lastNotificationId) {
                Log.d(TAG, "saving newest noti id: $lastNotificationId")
                account.lastNotificationId = lastNotificationId
                accountManager.saveAccount(account)
            }
        }
    }

    private fun update(newNotifications: List<Notification>, fromId: String?) {
        if (isEmpty(newNotifications)) {
            updateAdapter()
            return
        }
        if (fromId != null) {
            bottomId = fromId
        }
        val liftedNew = liftNotificationList(newNotifications).toMutableList()
        if (notifications.isEmpty()) {
            notifications.addAll(liftedNew)
        } else {
            val index = notifications.indexOf(liftedNew.last())
            if (index > 0) {
                notifications.subList(0, index).clear()
            }
            val newIndex = liftedNew.indexOf(notifications[0])
            if (newIndex == -1) {
                if (index == -1 && liftedNew.size >= LOAD_AT_ONCE) {
                    liftedNew.add(Left(newPlaceholder()))
                }
                notifications.addAll(0, liftedNew)
            } else {
                notifications.addAll(0, liftedNew.subList(0, newIndex))
            }
        }
        updateAdapter()
    }

    private fun addItems(newNotifications: List<Notification>, fromId: String?) {
        bottomId = fromId
        if (isEmpty(newNotifications)) {
            return
        }
        val end = notifications.size
        val liftedNew = liftNotificationList(newNotifications)
        val last = notifications[end - 1]
        if (!liftedNew.contains(last)) {
            notifications.addAll(liftedNew)
            updateAdapter()
        }
    }

    private fun replacePlaceholderWithNotifications(
        newNotifications: List<Notification>,
        pos: Int
    ) {
        // Remove placeholder
        notifications.removeAt(pos)
        if (isEmpty(newNotifications)) {
            updateAdapter()
            return
        }
        val liftedNew = liftNotificationList(newNotifications).toMutableList()

        // If we fetched less posts than in the limit, it means that the hole is not filled
        // If we fetched at least as much it means that there are more posts to load and we should
        // insert new placeholder
        if (newNotifications.size >= LOAD_AT_ONCE) {
            liftedNew.add(Left(newPlaceholder()))
        }
        notifications.addAll(pos, liftedNew)
        updateAdapter()
    }

    private val notificationLifter: (Notification) -> Right<Placeholder, Notification> =
        { value: Notification -> Right(value) }

    private fun liftNotificationList(list: List<Notification>): List<Either<Placeholder, Notification>> {
        return list.map(notificationLifter)
    }

    private fun fullyRefreshWithProgressBar(isShow: Boolean) {
        resetNotificationsLoad()
        if (isShow) {
            binding.progressBar.visibility = View.VISIBLE
            binding.statusView.visibility = View.GONE
        }
        updateAdapter()
        sendFetchNotificationsRequest(null, null, FetchEnd.TOP, -1)
    }

    private fun fullyRefresh() {
        fullyRefreshWithProgressBar(false)
    }

    private fun updateAdapter() {
        differ.submitList(notifications.pairedCopy)
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
        AsyncDifferConfig.Builder(diffCallback).build()
    )
    private val dataSource: AdapterDataSource<NotificationViewData> =
        object : AdapterDataSource<NotificationViewData> {
            override fun getItemCount(): Int {
                return differ.currentList.size
            }

            override fun getItemAt(pos: Int): NotificationViewData {
                return differ.currentList[pos]
            }
        }

    override fun onResume() {
        super.onResume()
        val rawAccountNotificationFilter = accountManager.activeAccount!!.notificationsFilter
        val accountNotificationFilter = deserialize(rawAccountNotificationFilter)
        if (notificationFilter != accountNotificationFilter) {
            loadNotificationsFilter()
            fullyRefreshWithProgressBar(true)
        }
        startUpdateTimestamp()
    }

    /**
     * Start to update adapter every minute to refresh timestamp
     * If setting absoluteTimeView is false
     * Auto dispose observable on pause
     */
    private fun startUpdateTimestamp() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)
        if (!useAbsoluteTime) {
            Observable.interval(0, 1, TimeUnit.MINUTES)
                .observeOn(AndroidSchedulers.mainThread())
                .to(
                    AutoDispose.autoDisposable(
                        AndroidLifecycleScopeProvider.from(
                            this,
                            Lifecycle.Event.ON_PAUSE
                        )
                    )
                )
                .subscribe { updateAdapter() }
        }
    }

    override fun onReselect() {
        jumpToTop()
    }

    companion object {
        private const val TAG = "NotificationF" // logging tag
        private const val LOAD_AT_ONCE = 30
        fun newInstance(): NotificationsFragment {
            val fragment = NotificationsFragment()
            val arguments = Bundle()
            fragment.arguments = arguments
            return fragment
        }

        private val diffCallback: DiffUtil.ItemCallback<NotificationViewData> =
            object : DiffUtil.ItemCallback<NotificationViewData>() {
                override fun areItemsTheSame(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData
                ): Boolean {
                    return oldItem.viewDataId == newItem.viewDataId
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
                    return if (oldItem.deepEquals(newItem)) {
                        //  If items are equal - update timestamp only
                        listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                    } else  // If items are different - update a whole view holder
                        null
                }
            }
    }
}