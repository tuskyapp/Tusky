/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.components.notifications

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.preference.PreferencesFragment
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.databinding.NotificationsFilterBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.StatusProvider
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationsFragment :
    SFragment(),
    SwipeRefreshLayout.OnRefreshListener,
    StatusActionListener,
    NotificationActionListener,
    AccountActionListener,
    MenuProvider,
    Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private val viewModel: NotificationsViewModel by viewModels { viewModelFactory }

    private lateinit var layoutManager: LayoutManager
    private lateinit var adapter: NotificationsPagingAdapter

    private var hideFab: Boolean = false
    private var showNotificationsFilterBar: Boolean = true
    private var readingOrder: PreferencesFragment.ReadingOrder = PreferencesFragment.ReadingOrder.NEWEST_FIRST

    /** see [com.keylesspalace.tusky.components.timeline.TimelineFragment] for explanation of the load more mechanism */
    private var loadMorePosition: Int? = null
    private var statusIdBelowLoadMore: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timeline_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        // setup the notifications filter bar
        showNotificationsFilterBar = preferences.getBoolean(PrefKeys.SHOW_NOTIFICATIONS_FILTER, true)
        updateFilterBarVisibility()
        binding.buttonClear.setOnClickListener { v -> confirmClearNotifications() }
        binding.buttonFilter.setOnClickListener { v -> showFilterMenu() }

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        // Setup the RecyclerView.
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        adapter = NotificationsPagingAdapter(
            accountId = accountManager.activeAccount!!.accountId,
            statusListener = this,
            notificationActionListener = this,
            accountActionListener = this,
            statusDisplayOptions = statusDisplayOptions
        )
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this,
                StatusProvider { pos: Int ->
                    if (pos in 0 until adapter.itemCount) {
                        val notification = adapter.peek(pos)
                        // We support replies only for now
                        if (notification is NotificationViewData.Concrete) {
                            return@StatusProvider notification.statusViewData
                        } else {
                            return@StatusProvider null
                        }
                    } else {
                        null
                    }
                }
            )
        )

        binding.recyclerView.adapter = adapter

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        hideFab = preferences.getBoolean(PrefKeys.FAB_HIDE, false)
        readingOrder = PreferencesFragment.ReadingOrder.from(preferences.getString(PrefKeys.READING_ORDER, null))

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
                        binding.statusView.setup((loadState.refresh as LoadState.Error).error) { onRefresh() }
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
                            binding.recyclerView.scrollBy(
                                0,
                                Utils.dpToPx(requireContext(), -30)
                            )
                        }
                    }
                }
                if (readingOrder == PreferencesFragment.ReadingOrder.OLDEST_FIRST) {
                    updateReadingPositionForOldestFirst()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notifications.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            eventHub.events.collect { event ->
                if (event is PreferenceChangedEvent) {
                    onPreferenceChanged(event.preferenceKey)
                }
            }
        }
    }

    override fun onRefresh() {
        adapter.refresh()
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        // not needed, muting via the more menu on statuses is handled in SFragment
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        // not needed, blocking via the more menu on statuses is handled in SFragment
    }

    override fun onRespondToFollowRequest(accept: Boolean, id: String, position: Int) {
        val notification = adapter.peek(position) ?: return
        val refreshAdapterFlow = viewModel.respondToFollowRequest(accept, accountId = id, notificationId = notification.id)
        lifecycleScope.launch {
            refreshAdapterFlow.collect { refreshAdapter ->
                if (refreshAdapter) {
                    adapter.refresh()
                }
            }
        }
    }

    override fun onViewReport(reportId: String?) {
        requireContext().openLink(
            "https://${accountManager.activeAccount!!.domain}/admin/reports/$reportId"
        )
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.reply(status.status)
    }

    override fun removeItem(position: Int) {
        val notification = adapter.peek(position) ?: return
        viewModel.remove(notification.id)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.reblog(reblog, status)
    }

    override val onMoreTranslate: (translate: Boolean, position: Int) -> Unit
        get() = { translate: Boolean, position: Int ->
            if (translate) {
                onTranslate(position)
            } else {
                onUntranslate(
                    position
                )
            }
        }

    private fun onTranslate(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        lifecycleScope.launch {
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
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.untranslate(status)
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.favorite(favourite, status)
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.bookmark(bookmark, status)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.voteInPoll(choices, status)
    }

    override fun clearWarningAction(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.clearWarning(status)
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.more(
            status.status,
            view,
            position,
            (status.translation as? TranslationViewData.Loaded)?.data
        )
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.peek(position)?.asStatusOrNull()?.status ?: return
        super.viewMedia(attachmentIndex, AttachmentViewData.list(status), view)
    }

    override fun onViewThread(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull()?.status ?: return
        super.viewThread(status.id, status.url)
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

    override fun onLoadMore(position: Int) {
        val placeholder = adapter.peek(position)?.asPlaceholderOrNull() ?: return
        loadMorePosition = position
        statusIdBelowLoadMore =
            if (position + 1 < adapter.itemCount) adapter.peek(position + 1)?.id else null
        viewModel.loadMore(placeholder.id)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentCollapsed(isCollapsed, status)
    }

    private fun confirmClearNotifications() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.notification_clear_text)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> clearNotifications() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearNotifications() {
        viewModel.clearNotifications()
    }

    private fun showFilterMenu() {
        val notificationTypeList = Notification.Type.visibleTypes.map { type ->
            getNotificationText(type)
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, notificationTypeList)
        val window = PopupWindow(requireContext())
        val menuBinding = NotificationsFilterBinding.inflate(LayoutInflater.from(requireContext()), binding.root as ViewGroup, false)

        menuBinding.buttonApply.setOnClickListener {
            val checkedItems = menuBinding.listView.getCheckedItemPositions()
            val excludes = Notification.Type.visibleTypes.filterIndexed { index, _ ->
                !checkedItems[index, false]
            }
            window.dismiss()
            viewModel.updateNotificationFilters(excludes.toSet())
        }

        menuBinding.listView.setAdapter(adapter)
        menuBinding.listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)

        Notification.Type.visibleTypes.forEachIndexed { index, type ->
            menuBinding.listView.setItemChecked(index, !viewModel.filters.value.contains(type))
        }

        window.setContentView(menuBinding.root)
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

    private fun onPreferenceChanged(key: String) {
        when (key) {
            PrefKeys.FAB_HIDE -> {
                hideFab = preferences.getBoolean(PrefKeys.FAB_HIDE, false)
            }

            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                }
            }

            PrefKeys.SHOW_NOTIFICATIONS_FILTER -> {
                if (isAdded) {
                    showNotificationsFilterBar = preferences.getBoolean(PrefKeys.SHOW_NOTIFICATIONS_FILTER, true)
                    updateFilterBarVisibility()
                }
            }
        }
    }

    private fun updateFilterBarVisibility() {
        val params = binding.swipeRefreshLayout.layoutParams as CoordinatorLayout.LayoutParams
        if (showNotificationsFilterBar) {
            binding.appBarOptions.setExpanded(true, false)
            binding.appBarOptions.show()
            // Set content behaviour to hide filter on scroll
            params.behavior = AppBarLayout.ScrollingViewBehavior()
        } else {
            binding.appBarOptions.setExpanded(false, false)
            binding.appBarOptions.hide()
            // Clear behaviour to hide app bar
            params.behavior = null
        }
    }

    private fun updateReadingPositionForOldestFirst() {
        var position = loadMorePosition ?: return
        val notificationIdBelowLoadMore = statusIdBelowLoadMore ?: return

        var notification: NotificationViewData?
        while (adapter.peek(position).let {
                notification = it
                it != null
            }
        ) {
            if (notification?.id == notificationIdBelowLoadMore) {
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

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_notifications, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                return true
            }
            R.id.action_edit_notification_filter -> {
                showFilterMenu()
                return true
            }
            R.id.action_clear_notifications -> {
                confirmClearNotifications()
                return true
            }
            else -> return false
        }
    }

    companion object {
        fun newInstance() = NotificationsFragment()
    }
}
