/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.arch.core.util.Function
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import autodispose2.AutoDispose
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountListActivity
import com.keylesspalace.tusky.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.databinding.FragmentViewThreadBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.PairedList
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.StatusProvider
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData.Companion.list
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.launch
import javax.inject.Inject

class ViewThreadFragment : SFragment(), OnRefreshListener, StatusActionListener, Injectable {

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var filterModel: FilterModel

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ViewThreadViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private lateinit var adapter: ThreadAdapter
    private var thisThreadsStatusId: String? = null
    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false
    private var statusIndex = 0
    private val statuses = PairedList<Status?, StatusViewData.Concrete> { status ->
        status.toViewData(
            alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
            alwaysOpenSpoiler,
            true
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thisThreadsStatusId = arguments!!.getString("id")
        val preferences = PreferenceManager.getDefaultSharedPreferences(
            activity!!
        )
        val statusDisplayOptions = StatusDisplayOptions(
            preferences.getBoolean("animateGifAvatars", false),
            accountManager.activeAccount!!.mediaPreviewEnabled,
            preferences.getBoolean("absoluteTimeView", false),
            preferences.getBoolean("showBotOverlay", true),
            preferences.getBoolean("useBlurhash", true),
            if (preferences.getBoolean(
                    "showCardsInTimelines",
                    false
                )
            ) CardViewMode.INDENTED else CardViewMode.NONE,
            preferences.getBoolean("confirmReblogs", true),
            preferences.getBoolean("confirmFavourites", false),
            preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )
        adapter = ThreadAdapter(statusDisplayOptions, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
       return inflater.inflate(R.layout.fragment_view_thread, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this,
                StatusProvider { index: Int -> statuses.getPairedItemOrNull(index) })
        )
        val divider = DividerItemDecoration(context, LinearLayout.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)
        binding.recyclerView.addItemDecoration(ConversationLineItemDecoration(context!!))
        alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        reloadFilters()
        binding.recyclerView.adapter = adapter
        statuses.clear()
        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    is ThreadUiState.Loading -> {}
                    is ThreadUiState.Error -> {}
                    is ThreadUiState.Success -> adapter.submitList(uiState.statuses)
                }
            }
        }

        viewModel.loadThread(thisThreadsStatusId!!)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        onRefresh()
        eventHub!!.events
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
                if (event is FavoriteEvent) {
                    handleFavEvent(event)
                } else if (event is ReblogEvent) {
                    handleReblogEvent(event)
                } else if (event is BookmarkEvent) {
                    handleBookmarkEvent(event)
                } else if (event is PinEvent) {
                    handlePinEvent(event)
                } else if (event is BlockEvent) {
                    removeAllByAccountId(event.accountId)
                } else if (event is StatusComposedEvent) {
                    handleStatusComposedEvent(event)
                } else if (event is StatusDeletedEvent) {
                    handleStatusDeletedEvent(event)
                }
            }
    }

    fun onRevealPressed() {
        val allExpanded = allExpanded()
        for (i in statuses.indices) {
            updateViewData(i, statuses.getPairedItem(i).copyWithExpanded(!allExpanded))
        }
        updateRevealIcon()
    }

    private fun allExpanded(): Boolean {
        var allExpanded = true
        for (i in statuses.indices) {
            if (!statuses.getPairedItem(i).isExpanded) {
                allExpanded = false
                break
            }
        }
        return allExpanded
    }

    override fun onRefresh() {


    }

    override fun onReply(position: Int) {
        super.reply(statuses[position])
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = statuses[position]
        timelineCases.reblog(statuses[position]!!.id, reblog)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                { status: Status -> replaceStatus(status) }
            ) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to reblog status: " + status!!.id, t
                )
            }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = statuses[position]
        timelineCases.favourite(statuses[position]!!.id, favourite)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                { status: Status -> replaceStatus(status) }
            ) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to favourite status: " + status!!.id, t
                )
            }
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = statuses[position]
        timelineCases.bookmark(statuses[position]!!.id, bookmark)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                { status: Status -> replaceStatus(status) }
            ) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to bookmark status: " + status!!.id, t
                )
            }
    }

    private fun replaceStatus(status: Status) {
        updateStatus(status.id) { status }
    }

    private fun updateStatus(statusId: String, mapper: Function<Status?, Status?>) {
        val position = indexOfStatus(statusId)
        if (position >= 0 && position < statuses.size) {
            val oldStatus = statuses[position]
            val newStatus = mapper.apply(oldStatus)
            val oldViewData = statuses.getPairedItem(position)
            statuses[position] = newStatus
            updateViewData(position, oldViewData.copyWithStatus(newStatus!!))
        }
    }

    override fun onMore(view: View, position: Int) {
        super.more(statuses[position]!!, view, position)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = statuses[position]
        super.viewMedia(attachmentIndex, list(status!!), view)
    }

    override fun onViewThread(position: Int) {
        val status = statuses[position]
        if (thisThreadsStatusId == status!!.id) {
            // If already viewing this thread, don't reopen it.
            return
        }
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onViewUrl(url: String) {
        var status: Status? = null
        if (!statuses.isEmpty()) {
            status = statuses[statusIndex]
        }
        if (status != null && status.url == url) {
            // already viewing the status with this url
            // probably just a preview federated and the user is clicking again to view more -> open the browser
            // this can happen with some friendica statuses
            requireContext().openLink(url)
            return
        }
        super.onViewUrl(url)
    }

    override fun onOpenReblog(position: Int) {
        // there should be no reblogs in the thread but let's implement it to be sure
        super.openReblog(statuses[position])
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        updateViewData(
            position,
            statuses.getPairedItem(position).copyWithExpanded(expanded)
        )
        updateRevealIcon()
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        updateViewData(
            position,
            statuses.getPairedItem(position).copyWithShowingContent(isShowing)
        )
    }

    private fun updateViewData(position: Int, newViewData: StatusViewData.Concrete) {
        statuses.setPairedItem(position, newViewData)
      //  adapter!!.setItem(position, newViewData, true)
    }

    override fun onLoadMore(position: Int) {}
    override fun onShowReblogs(position: Int) {
        val statusId = statuses[position]!!.id
        val intent = newIntent(context!!, AccountListActivity.Type.REBLOGGED, statusId)
        (activity as BaseActivity?)!!.startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = statuses[position]!!.id
        val intent = newIntent(context!!, AccountListActivity.Type.FAVOURITED, statusId)
        (activity as BaseActivity?)!!.startActivityWithSlideInAnimation(intent)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
     /*   adapter!!.setItem(
            position,
            statuses.getPairedItem(position).copyWithCollapsed(isCollapsed),
            true
        )*/
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    public override fun removeItem(position: Int) {
        if (position == statusIndex) {
            //the status got removed, close the activity
            activity!!.finish()
        }
        statuses.removeAt(position)
       // adapter!!.setStatuses(statuses.pairedCopy)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val (id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, poll) = statuses[position]!!.actionableStatus
        setVoteForPoll(id, poll!!.votedCopy(choices))
        timelineCases.voteInPoll(id, poll.id, choices)
            .observeOn(AndroidSchedulers.mainThread())
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                { newPoll: Poll -> setVoteForPoll(id, newPoll) }
            ) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to vote in poll: $id", t
                )
            }
    }

    private fun setVoteForPoll(statusId: String, newPoll: Poll) {
        updateStatus(statusId) { s: Status? -> s!!.copyWithPoll(newPoll) }
    }

    private fun removeAllByAccountId(accountId: String) {
        var status: Status? = null
        if (!statuses.isEmpty()) {
            status = statuses[statusIndex]
        }
        // using iterator to safely remove items while iterating
        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            if (s!!.account.id == accountId || s.actionableStatus.account.id == accountId) {
                iterator.remove()
            }
        }
        statusIndex = statuses.indexOf(status)
        if (statusIndex == -1) {
            //the status got removed, close the activity
            activity!!.finish()
            return
        }
      //  adapter!!.setDetailedStatusPosition(statusIndex)
      //  adapter!!.setStatuses(statuses.pairedCopy)
    }

    private fun onThreadRequestFailure(id: String?, throwable: Throwable) {
        val view = view
        binding.swipeRefreshLayout.isRefreshing = false
        if (view != null) {
            Snackbar.make(view, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry) { v: View? ->
                   // sendThreadRequest(id)
                   // sendStatusRequest(id)
                }
                .show()
        } else {
            Log.e(TAG, "Network request failed", throwable)
        }
    }

    private fun setStatus(status: Status): Int {
        if (statuses.size > 0 && statusIndex < statuses.size && statuses[statusIndex]!!.id == status.id) {
            // Do not add this status on refresh, it's already in there.
            statuses[statusIndex] = status
            return statusIndex
        }
        val i = statusIndex
        statuses.add(i, status)
        //adapter!!.setDetailedStatusPosition(i)
        //adapter!!.addItem(i, statuses.getPairedItem(i))
        updateRevealIcon()
        return i
    }

    private fun handleFavEvent(event: FavoriteEvent) {
        updateStatus(event.statusId) { s: Status? ->
            s!!.favourited = event.favourite
            s
        }
    }

    private fun handleReblogEvent(event: ReblogEvent) {
        updateStatus(event.statusId) { s: Status? ->
            s!!.reblogged = event.reblog
            s
        }
    }

    private fun handleBookmarkEvent(event: BookmarkEvent) {
        updateStatus(event.statusId) { s: Status? ->
            s!!.bookmarked = event.bookmark
            s
        }
    }

    private fun handlePinEvent(event: PinEvent) {
        updateStatus(event.statusId) { s: Status? -> s!!.copyWithPinned(event.pinned) }
    }

    private fun handleStatusComposedEvent(event: StatusComposedEvent) {
        val eventStatus = event.status
        if (eventStatus.inReplyToId == null) return
        if (eventStatus.inReplyToId == thisThreadsStatusId) {
            insertStatus(eventStatus, statuses.size)
        } else {
            // If new status is a reply to some status in the thread, insert new status after it
            // We only check statuses below main status, ones on top don't belong to this thread
            for (i in statusIndex until statuses.size) {
                val status = statuses[i]
                if (eventStatus.inReplyToId == status!!.id) {
                    insertStatus(eventStatus, i + 1)
                    break
                }
            }
        }
    }

    private fun insertStatus(status: Status, at: Int) {
        statuses.add(at, status)
       // adapter!!.addItem(at, statuses.getPairedItem(at))
    }

    private fun handleStatusDeletedEvent(event: StatusDeletedEvent) {
       /* val index = indexOfStatus(event.statusId)
        if (index != -1) {
            statuses.removeAt(index)
            adapter!!.removeItem(index)
        }*/
    }

    private fun indexOfStatus(statusId: String): Int {
        return statuses.indexOfFirst { s: Status? -> s!!.id == statusId }
    }

    private fun updateRevealIcon() {
        val activity = activity as ViewThreadActivity? ?: return
        var hasAnyWarnings = false
        // Statuses are updated from the main thread so nothing should change while iterating
        for (i in statuses.indices) {
            if (!TextUtils.isEmpty(statuses[i]!!.spoilerText)) {
                hasAnyWarnings = true
                break
            }
        }
        if (!hasAnyWarnings) {
            activity.setRevealButtonState(ViewThreadActivity.REVEAL_BUTTON_HIDDEN)
            return
        }
        activity.setRevealButtonState(if (allExpanded()) ViewThreadActivity.REVEAL_BUTTON_HIDE else ViewThreadActivity.REVEAL_BUTTON_REVEAL)
    }

    private fun reloadFilters() {
        mastodonApi.getFilters()
            .to(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
            .subscribe(
                { filters: List<Filter> ->
                    val relevantFilters = filters.filter { (_, _, context): Filter ->
                        context.contains(
                            Filter.THREAD
                        )
                    }
                    filterModel!!.initWithFilters(relevantFilters)
                    binding.recyclerView.post { applyFilters() }
                }
            ) { t: Throwable? -> Log.e(TAG, "Failed to load filters", t) }
    }

    private fun applyFilters() {
        // statuses.removeAll({ status: Status -> filterModel!!.shouldFilterStatus(status) })
       // adapter!!.setStatuses(statuses.pairedCopy)
    }

    companion object {
        private const val TAG = "ViewThreadFragment"
        fun newInstance(id: String): ViewThreadFragment {
            val arguments = Bundle(1)
            val fragment = ViewThreadFragment()
            arguments.putString("id", id)
            fragment.arguments = arguments
            return fragment
        }
    }
}