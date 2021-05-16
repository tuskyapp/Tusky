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

package com.keylesspalace.tusky.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
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
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider.from
import autodispose2.autoDispose
import com.keylesspalace.tusky.AccountListActivity
import com.keylesspalace.tusky.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.adapter.TimelineAdapter
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.DomainMuteEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.MuteConversationEvent
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.repository.Placeholder
import com.keylesspalace.tusky.repository.TimelineRepository
import com.keylesspalace.tusky.repository.TimelineRequestMode
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.Either.Left
import com.keylesspalace.tusky.util.Either.Right
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.LinkHelper
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.PairedList
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.util.dec
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.inc
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TimelineFragment : SFragment(), OnRefreshListener, StatusActionListener, Injectable, ReselectableFragment, RefreshableFragment {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var timelineRepo: TimelineRepository

    @Inject
    lateinit var accountManager: AccountManager

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private var kind: Kind? = null
    private var id: String? = null
    private var tags: List<String> = emptyList()

    private lateinit var adapter: TimelineAdapter

    private var isSwipeToRefreshEnabled = true
    private var isNeedRefresh = false

    private var eventRegistered = false

    /**
     * For some timeline kinds we must use LINK headers and not just status ids.
     */
    private var nextId: String? = null
    private var layoutManager: LinearLayoutManager? = null
    private var scrollListener: EndlessOnScrollListener? = null
    private var filterRemoveReplies = false
    private var filterRemoveReblogs = false
    private var hideFab = false
    private var bottomLoading = false
    private var didLoadEverythingBottom = false
    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false
    private var initialUpdateFailed = false

    private val statuses = PairedList<Either<Placeholder, Status>, StatusViewData> { input ->
        val status = input.asRightOrNull()
        if (status != null) {
            ViewDataUtils.statusToViewData(
                    status,
                    alwaysShowSensitiveMedia,
                    alwaysOpenSpoiler
            )
        } else {
            val (id1) = input.asLeft()
            StatusViewData.Placeholder(id1, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = requireArguments()
        kind = Kind.valueOf(arguments.getString(KIND_ARG)!!)
        if (kind == Kind.USER || kind == Kind.USER_PINNED || kind == Kind.USER_WITH_REPLIES || kind == Kind.LIST) {
            id = arguments.getString(ID_ARG)!!
        }
        if (kind == Kind.TAG) {
            tags = arguments.getStringArrayList(HASHTAGS_ARG)!!
        }

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val statusDisplayOptions = StatusDisplayOptions(
                animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
                mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
                useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
                showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
                useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
                cardViewMode = if (preferences.getBoolean(PrefKeys.SHOW_CARDS_IN_TIMELINES, false)) CardViewMode.INDENTED else CardViewMode.NONE,
                confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
                hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
                animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )
        adapter = TimelineAdapter(dataSource, statusDisplayOptions, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSwipeRefreshLayout()
        setupRecyclerView()
        updateAdapter()
        setupTimelinePreferences()
        if (statuses.isEmpty()) {
            binding.progressBar.show()
            bottomLoading = true
            sendInitialRequest()
        } else {
            binding.progressBar.hide()
            if (isNeedRefresh) {
                onRefresh()
            }
        }
    }

    private fun sendInitialRequest() {
        if (kind == Kind.HOME) {
            tryCache()
        } else {
            sendFetchTimelineRequest(null, null, null, FetchEnd.BOTTOM, -1)
        }
    }

    private fun tryCache() {
        // Request timeline from disk to make it quick, then replace it with timeline from
        // the server to update it
        timelineRepo.getStatuses(null, null, null, LOAD_AT_ONCE, TimelineRequestMode.DISK)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe { statuses: List<Either<Placeholder, Status>> ->
                    val mutableStatusResponse = statuses.toMutableList()
                    filterStatuses(mutableStatusResponse)
                    if (statuses.size > 1) {
                        clearPlaceholdersForResponse(mutableStatusResponse)
                        this.statuses.clear()
                        this.statuses.addAll(statuses)
                        updateAdapter()
                        binding.progressBar.hide()
                        // Request statuses including current top to refresh all of them
                    }
                    updateCurrent()
                    loadAbove()
                }
    }

    private fun updateCurrent() {
        if (statuses.isEmpty()) {
            return
        }
        val topId = statuses.first { status -> status.isRight() }!!.asRight().id
        timelineRepo.getStatuses(topId, null, null, LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe(
                        { statuses: List<Either<Placeholder, Status>> ->

                            initialUpdateFailed = false
                            // When cached timeline is too old, we would replace it with nothing
                            if (statuses.isNotEmpty()) {
                                val mutableStatuses = statuses.toMutableList()
                                filterStatuses(mutableStatuses)
                                if (!this.statuses.isEmpty()) {
                                    // clear old cached statuses
                                    val iterator = this.statuses.iterator()
                                    while (iterator.hasNext()) {
                                        val item = iterator.next()
                                        if (item.isRight()) {
                                            val (id1) = item.asRight()
                                            if (id1.length < topId.length || id1 < topId) {
                                                iterator.remove()
                                            }
                                        } else {
                                            val (id1) = item.asLeft()
                                            if (id1.length < topId.length || id1 < topId) {
                                                iterator.remove()
                                            }
                                        }
                                    }
                                }
                                this.statuses.addAll(mutableStatuses)
                                updateAdapter()
                            }
                            bottomLoading = false
                        },
                        { t: Throwable? ->
                            Log.d(TAG, "Failed updating timeline", t)
                            initialUpdateFailed = true
                            // Indicate that we are not loading anymore
                            binding.progressBar.hide()
                            binding.swipeRefreshLayout.isRefreshing = false
                        })
    }

    private fun setupTimelinePreferences() {
        alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        if (kind == Kind.HOME) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            filterRemoveReplies = !preferences.getBoolean("tabFilterHomeReplies", true)
            filterRemoveReblogs = !preferences.getBoolean("tabFilterHomeBoosts", true)
        }
        reloadFilters(false)
    }

    override fun filterIsRelevant(filter: Filter): Boolean {
        return filterContextMatchesKind(kind, filter.context)
    }

    override fun refreshAfterApplyingFilters() {
        fullyRefresh()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setAccessibilityDelegateCompat(
                ListStatusAccessibilityDelegate(binding.recyclerView, this)
                { pos -> statuses.getPairedItemOrNull(pos) }
        )
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        val divider = DividerItemDecoration(context, RecyclerView.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    private fun deleteStatusById(id: String) {
        for (i in statuses.indices) {
            val either = statuses[i]
            if (either.isRight() && id == either.asRight().id) {
                statuses.remove(either)
                updateAdapter()
                break
            }
        }
        if (statuses.isEmpty()) {
            showEmptyView()
        }
    }

    private fun showEmptyView() {
        binding.statusView.show()
        binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then. */
        scrollListener = if (actionButtonPresent()) {
            /* Use a modified scroll listener that both loads more statuses as it goes, and hides
             * the follow button on down-scroll. */
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            hideFab = preferences.getBoolean("fabHide", false)
            object : EndlessOnScrollListener(layoutManager) {
                override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(view, dx, dy)
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

                override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                    this@TimelineFragment.onLoadMore()
                }
            }
        } else {
            // Just use the basic scroll listener to load more statuses.
            object : EndlessOnScrollListener(layoutManager) {
                override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                    this@TimelineFragment.onLoadMore()
                }
            }
        }.also {
            binding.recyclerView.addOnScrollListener(it)
        }

        if (!eventRegistered) {
            eventHub.events
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(from(this, Lifecycle.Event.ON_DESTROY))
                    .subscribe { event: Event? ->
                        when (event) {
                            is FavoriteEvent -> handleFavEvent(event)
                            is ReblogEvent -> handleReblogEvent(event)
                            is BookmarkEvent -> handleBookmarkEvent(event)
                            is MuteConversationEvent -> fullyRefresh()
                            is UnfollowEvent -> {
                                if (kind == Kind.HOME) {
                                    val id = event.accountId
                                    removeAllByAccountId(id)
                                }
                            }
                            is BlockEvent -> {
                                if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                    val id = event.accountId
                                    removeAllByAccountId(id)
                                }
                            }
                            is MuteEvent -> {
                                if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                    val id = event.accountId
                                    removeAllByAccountId(id)
                                }
                            }
                            is DomainMuteEvent -> {
                                if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                    val instance = event.instance
                                    removeAllByInstance(instance)
                                }
                            }
                            is StatusDeletedEvent -> {
                                if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                    val id = event.statusId
                                    deleteStatusById(id)
                                }
                            }
                            is StatusComposedEvent -> {
                                val status = event.status
                                handleStatusComposeEvent(status)
                            }
                            is PreferenceChangedEvent -> {
                                onPreferenceChanged(event.preferenceKey)
                            }
                        }
                    }
            eventRegistered = true
        }
    }

    override fun onRefresh() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.statusView.hide()
        isNeedRefresh = false
        if (initialUpdateFailed) {
            updateCurrent()
        }
        loadAbove()
    }

    private fun loadAbove() {
        var firstOrNull: String? = null
        var secondOrNull: String? = null
        for (i in statuses.indices) {
            val status = statuses[i]
            if (status.isRight()) {
                firstOrNull = status.asRight().id
                if (i + 1 < statuses.size && statuses[i + 1].isRight()) {
                    secondOrNull = statuses[i + 1].asRight().id
                }
                break
            }
        }
        if (firstOrNull != null) {
            sendFetchTimelineRequest(null, firstOrNull, secondOrNull, FetchEnd.TOP, -1)
        } else {
            sendFetchTimelineRequest(null, null, null, FetchEnd.BOTTOM, -1)
        }
    }

    override fun onReply(position: Int) {
        super.reply(statuses[position].asRight())
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = statuses[position].asRight()
        timelineCases.reblog(status, reblog)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe(
                        { newStatus: Status -> setRebloggedForStatus(position, newStatus, reblog) }
                ) { t: Throwable? -> Log.d(TAG, "Failed to reblog status " + status.id, t) }
    }

    private fun setRebloggedForStatus(position: Int, status: Status, reblog: Boolean) {
        status.reblogged = reblog
        if (status.reblog != null) {
            status.reblog.reblogged = reblog
        }
        val actual = findStatusAndPosition(position, status) ?: return
        val newViewData: StatusViewData = StatusViewData.Builder(actual.first)
                .setReblogged(reblog)
                .createStatusViewData()
        statuses.setPairedItem(actual.second!!, newViewData)
        updateAdapter()
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = statuses[position].asRight()
        timelineCases.favourite(status, favourite)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe(
                        { newStatus: Status -> setFavouriteForStatus(position, newStatus, favourite) },
                        { t: Throwable? -> Log.d(TAG, "Failed to favourite status " + status.id, t) }
                )
    }

    private fun setFavouriteForStatus(position: Int, status: Status, favourite: Boolean) {
        status.favourited = favourite
        if (status.reblog != null) {
            status.reblog.favourited = favourite
        }
        val actual = findStatusAndPosition(position, status) ?: return
        val newViewData: StatusViewData = StatusViewData.Builder(actual.first)
                .setFavourited(favourite)
                .createStatusViewData()
        statuses.setPairedItem(actual.second!!, newViewData)
        updateAdapter()
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = statuses[position].asRight()
        timelineCases.bookmark(status, bookmark)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe(
                        { newStatus: Status -> setBookmarkForStatus(position, newStatus, bookmark) },
                        { t: Throwable? -> Log.d(TAG, "Failed to favourite status " + status.id, t) }
                )
    }

    private fun setBookmarkForStatus(position: Int, status: Status, bookmark: Boolean) {
        status.bookmarked = bookmark
        if (status.reblog != null) {
            status.reblog.bookmarked = bookmark
        }
        val actual = findStatusAndPosition(position, status) ?: return
        val newViewData: StatusViewData = StatusViewData.Builder(actual.first)
                .setBookmarked(bookmark)
                .createStatusViewData()
        statuses.setPairedItem(actual.second!!, newViewData)
        updateAdapter()
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = statuses[position].asRight()
        val votedPoll = status.actionableStatus.poll!!.votedCopy(choices)
        setVoteForPoll(position, status, votedPoll)
        timelineCases.voteInPoll(status, choices)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe(
                        { newPoll: Poll -> setVoteForPoll(position, status, newPoll) },
                        { t: Throwable? -> Log.d(TAG, "Failed to vote in poll: " + status.id, t) }
                )
    }

    private fun setVoteForPoll(position: Int, status: Status, newPoll: Poll) {
        val actual = findStatusAndPosition(position, status) ?: return
        val newViewData: StatusViewData = StatusViewData.Builder(actual.first)
                .setPoll(newPoll)
                .createStatusViewData()
        statuses.setPairedItem(actual.second!!, newViewData)
        updateAdapter()
    }

    override fun onMore(view: View, position: Int) {
        super.more(statuses[position].asRight(), view, position)
    }

    override fun onOpenReblog(position: Int) {
        super.openReblog(statuses[position].asRight())
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val newViewData: StatusViewData = StatusViewData.Builder(
                statuses.getPairedItem(position) as StatusViewData.Concrete)
                .setIsExpanded(expanded).createStatusViewData()
        statuses.setPairedItem(position, newViewData)
        updateAdapter()
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val newViewData: StatusViewData = StatusViewData.Builder(
                statuses.getPairedItem(position) as StatusViewData.Concrete)
                .setIsShowingSensitiveContent(isShowing).createStatusViewData()
        statuses.setPairedItem(position, newViewData)
        updateAdapter()
    }

    override fun onShowReblogs(position: Int) {
        val statusId = statuses[position].asRight().id
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = statuses[position].asRight().id
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onLoadMore(position: Int) {
        //check bounds before accessing list,
        if (statuses.size >= position && position > 0) {
            val fromStatus = statuses[position - 1].asRightOrNull()
            val toStatus = statuses[position + 1].asRightOrNull()
            val maxMinusOne = if (statuses.size > position + 1 && statuses[position + 2].isRight()) statuses[position + 1].asRight().id else null
            if (fromStatus == null || toStatus == null) {
                Log.e(TAG, "Failed to load more at $position, wrong placeholder position")
                return
            }
            sendFetchTimelineRequest(fromStatus.id, toStatus.id, maxMinusOne,
                    FetchEnd.MIDDLE, position)
            val (id1) = statuses[position].asLeft()
            val newViewData: StatusViewData = StatusViewData.Placeholder(id1, true)
            statuses.setPairedItem(position, newViewData)
            updateAdapter()
        } else {
            Log.e(TAG, "error loading more")
        }
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        if (position < 0 || position >= statuses.size) {
            Log.e(TAG, String.format("Tried to access out of bounds status position: %d of %d", position, statuses.size - 1))
            return
        }
        val status = statuses.getPairedItem(position)
        if (status !is StatusViewData.Concrete) {
            // Statuses PairedList contains a base type of StatusViewData.Concrete and also doesn't
            // check for null values when adding values to it although this doesn't seem to be an issue.
            Log.e(TAG, String.format(
                    "Expected StatusViewData.Concrete, got %s instead at position: %d of %d",
                    status?.javaClass?.simpleName ?: "<null>",
                    position,
                    statuses.size - 1
            ))
            return
        }
        val updatedStatus: StatusViewData = StatusViewData.Builder(status)
                .setCollapsed(isCollapsed)
                .createStatusViewData()
        statuses.setPairedItem(position, updatedStatus)
        updateAdapter()
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = statuses.getOrNull(position)?.asRightOrNull() ?: return
        super.viewMedia(attachmentIndex, status, view)
    }

    override fun onViewThread(position: Int) {
        super.viewThread(statuses[position].asRight())
    }

    override fun onViewTag(tag: String) {
        if (kind == Kind.TAG && tags.size == 1 && tags.contains(tag)) {
            // If already viewing a tag page, then ignore any request to view that tag again.
            return
        }
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        if ((kind == Kind.USER || kind == Kind.USER_WITH_REPLIES) && this.id == id) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return
        }
        super.viewAccount(id)
    }

    private fun onPreferenceChanged(key: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        when (key) {
            PrefKeys.FAB_HIDE -> {
                hideFab = sharedPreferences.getBoolean(PrefKeys.FAB_HIDE, false)
            }
            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                    fullyRefresh()
                }
            }
            PrefKeys.TAB_FILTER_HOME_REPLIES -> {
                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
                val oldRemoveReplies = filterRemoveReplies
                filterRemoveReplies = kind == Kind.HOME && !filter
                if (adapter.itemCount > 1 && oldRemoveReplies != filterRemoveReplies) {
                    fullyRefresh()
                }
            }
            PrefKeys.TAB_FILTER_HOME_BOOSTS -> {
                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
                val oldRemoveReblogs = filterRemoveReblogs
                filterRemoveReblogs = kind == Kind.HOME && !filter
                if (adapter.itemCount > 1 && oldRemoveReblogs != filterRemoveReblogs) {
                    fullyRefresh()
                }
            }
            Filter.HOME, Filter.NOTIFICATIONS, Filter.THREAD, Filter.PUBLIC, Filter.ACCOUNT -> {
                if (filterContextMatchesKind(kind, listOf(key))) {
                    reloadFilters(true)
                }
            }
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> {
                //it is ok if only newly loaded statuses are affected, no need to fully refresh
                alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
            }
        }
    }

    public override fun removeItem(position: Int) {
        statuses.removeAt(position)
        updateAdapter()
    }

    private fun removeAllByAccountId(accountId: String) {
        // using iterator to safely remove items while iterating
        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next().asRightOrNull()
            if (status != null &&
                    (status.account.id == accountId || status.actionableStatus.account.id == accountId)) {
                iterator.remove()
            }
        }
        updateAdapter()
    }

    private fun removeAllByInstance(instance: String) {
        // using iterator to safely remove items while iterating
        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next().asRightOrNull()
            if (status != null && LinkHelper.getDomain(status.account.url) == instance) {
                iterator.remove()
            }
        }
        updateAdapter()
    }

    private fun onLoadMore() {
        if (didLoadEverythingBottom || bottomLoading) {
            return
        }
        if (statuses.isEmpty()) {
            sendInitialRequest()
            return
        }
        bottomLoading = true
        val last = statuses[statuses.size - 1]
        val placeholder: Placeholder
        if (last!!.isRight()) {
            val placeholderId = last.asRight().id.dec()
            placeholder = Placeholder(placeholderId)
            statuses.add(Left(placeholder))
        } else {
            placeholder = last.asLeft()
        }
        statuses.setPairedItem(statuses.size - 1,
                StatusViewData.Placeholder(placeholder.id, true))
        updateAdapter()

        val bottomId: String? = if (kind == Kind.FAVOURITES || kind == Kind.BOOKMARKS) {
            nextId
        } else {
            statuses.lastOrNull { it.isRight() }?.asRight()?.id
        }

        sendFetchTimelineRequest(bottomId, null, null, FetchEnd.BOTTOM, -1)
    }

    private fun fullyRefresh() {
        statuses.clear()
        updateAdapter()
        bottomLoading = true
        sendFetchTimelineRequest(null, null, null, FetchEnd.BOTTOM, -1)
    }

    private fun actionButtonPresent(): Boolean {
        return kind != Kind.TAG && kind != Kind.FAVOURITES && kind != Kind.BOOKMARKS &&
                activity is ActionButtonActivity
    }

    private fun getFetchCallByTimelineType(fromId: String?, uptoId: String?): Single<Response<List<Status>>> {
        val api = mastodonApi
        return when (kind) {
            Kind.HOME -> api.homeTimeline(fromId, uptoId, LOAD_AT_ONCE)
            Kind.PUBLIC_FEDERATED -> api.publicTimeline(null, fromId, uptoId, LOAD_AT_ONCE)
            Kind.PUBLIC_LOCAL -> api.publicTimeline(true, fromId, uptoId, LOAD_AT_ONCE)
            Kind.TAG -> {
                val firstHashtag = tags[0]
                val additionalHashtags = tags.subList(1, tags.size)
                api.hashtagTimeline(firstHashtag, additionalHashtags, null, fromId, uptoId, LOAD_AT_ONCE)
            }
            Kind.USER -> api.accountStatuses(id!!, fromId, uptoId, LOAD_AT_ONCE, true, null, null)
            Kind.USER_PINNED -> api.accountStatuses(id!!, fromId, uptoId, LOAD_AT_ONCE, null, null, true)
            Kind.USER_WITH_REPLIES -> api.accountStatuses(id!!, fromId, uptoId, LOAD_AT_ONCE, null, null, null)
            Kind.FAVOURITES -> api.favourites(fromId, uptoId, LOAD_AT_ONCE)
            Kind.BOOKMARKS -> api.bookmarks(fromId, uptoId, LOAD_AT_ONCE)
            Kind.LIST -> api.listTimeline(id!!, fromId, uptoId, LOAD_AT_ONCE)
            else -> api.homeTimeline(fromId, uptoId, LOAD_AT_ONCE)
        }
    }

    private fun sendFetchTimelineRequest(maxId: String?, sinceId: String?,
                                         sinceIdMinusOne: String?,
                                         fetchEnd: FetchEnd, pos: Int) {
        if (isAdded && (fetchEnd == FetchEnd.TOP || fetchEnd == FetchEnd.BOTTOM && maxId == null && binding.progressBar.visibility != View.VISIBLE) && !isSwipeToRefreshEnabled) {
            binding.topProgressBar.show()
        }
        if (kind == Kind.HOME) {
            // allow getting old statuses/fallbacks for network only for for bottom loading
            val mode = if (fetchEnd == FetchEnd.BOTTOM) {
                TimelineRequestMode.ANY
            } else {
                TimelineRequestMode.NETWORK
            }
            timelineRepo.getStatuses(maxId, sinceId, sinceIdMinusOne, LOAD_AT_ONCE, mode)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(from(this))
                    .subscribe(
                            { result: List<Either<Placeholder, Status>> -> onFetchTimelineSuccess(result.toMutableList(), fetchEnd, pos) },
                            { t: Throwable -> onFetchTimelineFailure(t, fetchEnd, pos) }
                    )
        } else {
            getFetchCallByTimelineType(maxId, sinceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(from(this))
                    .subscribe(
                            { response: Response<List<Status>> ->
                                if (response.isSuccessful) {
                                    val newNextId = extractNextId(response)
                                    if (newNextId != null) {
                                        // when we reach the bottom of the list, we won't have a new link. If
                                        // we blindly write `null` here we will start loading from the top
                                        // again.
                                        nextId = newNextId
                                    }
                                    onFetchTimelineSuccess(liftStatusList(response.body()!!).toMutableList(), fetchEnd, pos)
                                } else {
                                    onFetchTimelineFailure(Exception(response.message()), fetchEnd, pos)
                                }
                            }
                    ) { t: Throwable -> onFetchTimelineFailure(t, fetchEnd, pos) }
        }
    }

    private fun extractNextId(response: Response<*>): String? {
        val linkHeader = response.headers()["Link"] ?: return null
        val links = HttpHeaderLink.parse(linkHeader)
        val nextHeader = HttpHeaderLink.findByRelationType(links, "next") ?: return null
        val nextLink = nextHeader.uri ?: return null
        return nextLink.getQueryParameter("max_id")
    }

    private fun onFetchTimelineSuccess(statuses: MutableList<Either<Placeholder, Status>>,
                                       fetchEnd: FetchEnd, pos: Int) {

        // We filled the hole (or reached the end) if the server returned less statuses than we
        // we asked for.
        val fullFetch = statuses.size >= LOAD_AT_ONCE
        filterStatuses(statuses)
        when (fetchEnd) {
            FetchEnd.TOP -> {
                updateStatuses(statuses, fullFetch)
            }
            FetchEnd.MIDDLE -> {
                replacePlaceholderWithStatuses(statuses, fullFetch, pos)
            }
            FetchEnd.BOTTOM -> {
                if (!this.statuses.isEmpty()
                        && !this.statuses[this.statuses.size - 1].isRight()) {
                    this.statuses.removeAt(this.statuses.size - 1)
                    updateAdapter()
                }
                if (statuses.isNotEmpty() && !statuses[statuses.size - 1].isRight()) {
                    // Removing placeholder if it's the last one from the cache
                    statuses.removeAt(statuses.size - 1)
                }
                val oldSize = this.statuses.size
                if (this.statuses.size > 1) {
                    addItems(statuses)
                } else {
                    updateStatuses(statuses, fullFetch)
                }
                if (this.statuses.size == oldSize) {
                    // This may be a brittle check but seems like it works
                    // Can we check it using headers somehow? Do all server support them?
                    didLoadEverythingBottom = true
                }
            }
        }
        if (isAdded) {
            binding.topProgressBar.hide()
            updateBottomLoadingState(fetchEnd)
            binding.progressBar.hide()
            binding.swipeRefreshLayout.isRefreshing = false
            binding.swipeRefreshLayout.isEnabled = true
            if (this.statuses.size == 0) {
                showEmptyView()
            } else {
                binding.statusView.hide()
            }
        }
    }

    private fun onFetchTimelineFailure(throwable: Throwable, fetchEnd: FetchEnd, position: Int) {
        if (isAdded) {
            binding.swipeRefreshLayout.isRefreshing = false
            binding.topProgressBar.hide()
            if (fetchEnd == FetchEnd.MIDDLE && !statuses[position].isRight()) {
                var placeholder = statuses[position].asLeftOrNull()
                val newViewData: StatusViewData
                if (placeholder == null) {
                    val (id1) = statuses[position - 1].asRight()
                    val newId = id1.dec()
                    placeholder = Placeholder(newId)
                }
                newViewData = StatusViewData.Placeholder(placeholder.id, false)
                statuses.setPairedItem(position, newViewData)
                updateAdapter()
            } else if (statuses.isEmpty()) {
                binding.swipeRefreshLayout.isEnabled = false
                binding.statusView.visibility = View.VISIBLE
                if (throwable is IOException) {
                    binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network) {
                        binding.progressBar.visibility = View.VISIBLE
                        onRefresh()
                    }
                } else {
                    binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic) {
                        binding.progressBar.visibility = View.VISIBLE
                        onRefresh()
                    }
                }
            }
            Log.e(TAG, "Fetch Failure: " + throwable.message)
            updateBottomLoadingState(fetchEnd)
            binding.progressBar.hide()
        }
    }

    private fun updateBottomLoadingState(fetchEnd: FetchEnd) {
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false
        }
    }

    private fun filterStatuses(statuses: MutableList<Either<Placeholder, Status>>) {
        val it = statuses.iterator()
        while (it.hasNext()) {
            val status = it.next().asRightOrNull()
            if (status != null
                    && (status.inReplyToId != null && filterRemoveReplies
                            || status.reblog != null && filterRemoveReblogs
                            || shouldFilterStatus(status.actionableStatus))) {
                it.remove()
            }
        }
    }

    private fun updateStatuses(newStatuses: MutableList<Either<Placeholder, Status>>, fullFetch: Boolean) {
        if (newStatuses.isEmpty()) {
            updateAdapter()
            return
        }
        if (statuses.isEmpty()) {
            statuses.addAll(newStatuses)
        } else {
            val lastOfNew = newStatuses[newStatuses.size - 1]
            val index = statuses.indexOf(lastOfNew)
            if (index >= 0) {
                statuses.subList(0, index).clear()
            }
            val newIndex = newStatuses.indexOf(statuses[0])
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    val placeholderId = newStatuses.last { status -> status.isRight() }.asRight().id.inc()
                    newStatuses.add(Left(Placeholder(placeholderId)))
                }
                statuses.addAll(0, newStatuses)
            } else {
                statuses.addAll(0, newStatuses.subList(0, newIndex))
            }
        }
        // Remove all consecutive placeholders
        removeConsecutivePlaceholders()
        updateAdapter()
    }

    private fun removeConsecutivePlaceholders() {
        for (i in 0 until statuses.size - 1) {
            if (statuses[i].isLeft() && statuses[i + 1].isLeft()) {
                statuses.removeAt(i)
            }
        }
    }

    private fun addItems(newStatuses: List<Either<Placeholder, Status>?>) {
        if (newStatuses.isEmpty()) {
            return
        }
        val last = statuses.last { status ->
            status.isRight()
        }

        // I was about to replace findStatus with indexOf but it is incorrect to compare value
        // types by ID anyway and we should change equals() for Status, I think, so this makes sense
        if (last != null && !newStatuses.contains(last)) {
            statuses.addAll(newStatuses)
            removeConsecutivePlaceholders()
            updateAdapter()
        }
    }

    /**
     * For certain requests we don't want to see placeholders, they will be removed some other way
     */
    private fun clearPlaceholdersForResponse(statuses: MutableList<Either<Placeholder, Status>>) {
        statuses.removeAll{ status -> status.isLeft() }
    }

    private fun replacePlaceholderWithStatuses(newStatuses: MutableList<Either<Placeholder, Status>>,
                                               fullFetch: Boolean, pos: Int) {
        val placeholder = statuses[pos]
        if (placeholder.isLeft()) {
            statuses.removeAt(pos)
        }
        if (newStatuses.isEmpty()) {
            updateAdapter()
            return
        }
        if (fullFetch) {
            newStatuses.add(placeholder)
        }
        statuses.addAll(pos, newStatuses)
        removeConsecutivePlaceholders()
        updateAdapter()
    }

    private fun findStatusOrReblogPositionById(statusId: String): Int {
        return statuses.indexOfFirst { either ->
            val status = either.asRightOrNull()
            status != null &&
                    (statusId == status.id ||
                            (status.reblog != null && statusId == status.reblog.id))
        }
    }

    private val statusLifter: Function1<Status, Either<Placeholder, Status>> = { value -> Right(value) }

    private fun findStatusAndPosition(position: Int, status: Status): Pair<StatusViewData.Concrete, Int>? {
        val statusToUpdate: StatusViewData.Concrete
        val positionToUpdate: Int
        val someOldViewData = statuses.getPairedItem(position)

        // Unlikely, but data could change between the request and response
        if (someOldViewData is StatusViewData.Placeholder ||
                (someOldViewData as StatusViewData.Concrete).id != status.id) {
            // try to find the status we need to update
            val foundPos = statuses.indexOf(Right(status))
            if (foundPos < 0) return null // okay, it's hopeless, give up
            statusToUpdate = statuses.getPairedItem(foundPos) as StatusViewData.Concrete
            positionToUpdate = position
        } else {
            statusToUpdate = someOldViewData
            positionToUpdate = position
        }
        return Pair(statusToUpdate, positionToUpdate)
    }

    private fun handleReblogEvent(reblogEvent: ReblogEvent) {
        val pos = findStatusOrReblogPositionById(reblogEvent.statusId)
        if (pos < 0) return
        val status = statuses[pos].asRight()
        setRebloggedForStatus(pos, status, reblogEvent.reblog)
    }

    private fun handleFavEvent(favEvent: FavoriteEvent) {
        val pos = findStatusOrReblogPositionById(favEvent.statusId)
        if (pos < 0) return
        val status = statuses[pos].asRight()
        setFavouriteForStatus(pos, status, favEvent.favourite)
    }

    private fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        val pos = findStatusOrReblogPositionById(bookmarkEvent.statusId)
        if (pos < 0) return
        val status = statuses[pos].asRight()
        setBookmarkForStatus(pos, status, bookmarkEvent.bookmark)
    }

    private fun handleStatusComposeEvent(status: Status) {
        when (kind) {
            Kind.HOME, Kind.PUBLIC_FEDERATED, Kind.PUBLIC_LOCAL -> onRefresh()
            Kind.USER, Kind.USER_WITH_REPLIES -> if (status.account.id == id) {
                onRefresh()
            } else {
                return
            }
            Kind.TAG, Kind.FAVOURITES, Kind.LIST, Kind.BOOKMARKS, Kind.USER_PINNED -> return
        }
    }

    private fun liftStatusList(list: List<Status>): List<Either<Placeholder, Status>> {
        return list.map(statusLifter)
    }

    private fun updateAdapter() {
        differ.submitList(statuses.pairedCopy)
    }

    private val listUpdateCallback: ListUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (isAdded) {
                adapter.notifyItemRangeInserted(position, count)
                val context = context
                // scroll up when new items at the top are loaded while being in the first position
                // https://github.com/tuskyapp/Tusky/pull/1905#issuecomment-677819724
                if (position == 0 && context != null && adapter.itemCount != count) {
                    if (isSwipeToRefreshEnabled) {
                        binding.recyclerView.scrollBy(0, Utils.dpToPx(context, -30))
                    } else binding.recyclerView.scrollToPosition(0)
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
    private val differ = AsyncListDiffer(listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build())

    private val dataSource: TimelineAdapter.AdapterDataSource<StatusViewData> = object : TimelineAdapter.AdapterDataSource<StatusViewData> {
        override fun getItemCount(): Int {
            return differ.currentList.size
        }

        override fun getItemAt(pos: Int): StatusViewData {
            return differ.currentList[pos]
        }
    }

    private var talkBackWasEnabled = false

    override fun onResume() {
        super.onResume()
        val a11yManager = ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Log.d(TAG, "talkback was enabled: $wasEnabled, now $talkBackWasEnabled")
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyDataSetChanged()
        }
        startUpdateTimestamp()
    }

    /**
     * Start to update adapter every minute to refresh timestamp
     * If setting absoluteTimeView is false
     * Auto dispose observable on pause
     */
    private fun startUpdateTimestamp() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false)
        if (!useAbsoluteTime) {
            Observable.interval(1, TimeUnit.MINUTES)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(from(this, Lifecycle.Event.ON_PAUSE))
                    .subscribe { updateAdapter() }
        }
    }

    override fun onReselect() {
        if (isAdded) {
            layoutManager!!.scrollToPosition(0)
            binding.recyclerView.stopScroll()
            scrollListener!!.reset()
        }
    }

    override fun refreshContent() {
        if (isAdded) {
            onRefresh()
        } else {
            isNeedRefresh = true
        }
    }

    enum class Kind {
        HOME, PUBLIC_LOCAL, PUBLIC_FEDERATED, TAG, USER, USER_PINNED, USER_WITH_REPLIES, FAVOURITES, LIST, BOOKMARKS
    }

    private enum class FetchEnd {
        TOP, BOTTOM, MIDDLE
    }

    companion object {
        private const val TAG = "TimelineF" // logging tag
        private const val KIND_ARG = "kind"
        private const val ID_ARG = "id"
        private const val HASHTAGS_ARG = "hashtags"
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "enableSwipeToRefresh"
        private const val LOAD_AT_ONCE = 30

        fun newInstance(kind: Kind, hashtagOrId: String? = null, enableSwipeToRefresh: Boolean = true): TimelineFragment {
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
            arguments.putString(KIND_ARG, Kind.TAG.name)
            arguments.putStringArrayList(HASHTAGS_ARG, ArrayList(hashtags))
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)
            fragment.arguments = arguments
            return fragment
        }

        private fun filterContextMatchesKind(kind: Kind?, filterContext: List<String>): Boolean {
            // home, notifications, public, thread
            return when (kind) {
                Kind.HOME, Kind.LIST -> filterContext.contains(Filter.HOME)
                Kind.PUBLIC_FEDERATED, Kind.PUBLIC_LOCAL, Kind.TAG -> filterContext.contains(Filter.PUBLIC)
                Kind.FAVOURITES -> filterContext.contains(Filter.PUBLIC) || filterContext.contains(Filter.NOTIFICATIONS)
                Kind.USER, Kind.USER_WITH_REPLIES, Kind.USER_PINNED -> filterContext.contains(Filter.ACCOUNT)
                else -> false
            }
        }

        private val diffCallback: DiffUtil.ItemCallback<StatusViewData> = object : DiffUtil.ItemCallback<StatusViewData>() {
            override fun areItemsTheSame(oldItem: StatusViewData, newItem: StatusViewData): Boolean {
                return oldItem.viewDataId == newItem.viewDataId
            }

            override fun areContentsTheSame(oldItem: StatusViewData, newItem: StatusViewData): Boolean {
                return false // Items are different always. It allows to refresh timestamp on every view holder update
            }

            override fun getChangePayload(oldItem: StatusViewData, newItem: StatusViewData): Any? {
                return if (oldItem.deepEquals(newItem)) {
                    // If items are equal - update timestamp only
                    listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                } else  // If items are different - update the whole view holder
                    null
            }
        }
    }
}