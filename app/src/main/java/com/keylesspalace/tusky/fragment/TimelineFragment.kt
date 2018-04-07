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

import android.arch.core.util.Function
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.TabLayout
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.util.Pair
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.FooterViewHolder
import com.keylesspalace.tusky.adapter.TimelineAdapter
import com.keylesspalace.tusky.appstore.AppStore
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.receiver.TimelineReceiver
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.uber.autodispose.android.scope
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject

class TimelineFragment : SFragment(), SwipeRefreshLayout.OnRefreshListener, StatusActionListener, SharedPreferences.OnSharedPreferenceChangeListener, Injectable {

    @Inject
    lateinit var timelineCases: TimelineCases
    @Inject
    lateinit var mastodonApi: MastodonApi
    @Inject
    lateinit var appStore: AppStore

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: TimelineAdapter
    private lateinit var kind: Kind
    private var hashtagOrId: String? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private var scrollListener: EndlessOnScrollListener? = null
    private var onTabSelectedListener: TabLayout.OnTabSelectedListener? = null
    private var filterRemoveReplies: Boolean = false
    private var filterRemoveReblogs: Boolean = false
    private var filterRemoveRegexMatcher: Matcher? = null
    private var hideFab: Boolean = false
    private var timelineReceiver: TimelineReceiver? = null
    private var topLoading: Boolean = false
    private var topFetches: Int = 0
    private var bottomLoading: Boolean = false
    private var bottomFetches: Int = 0
    private var bottomId: String? = null
    private var topId: String? = null

    private var alwaysShowSensitiveMedia: Boolean = false

    private val statuses = PairedList(Function<Either<Placeholder, Status>, StatusViewData> { input ->
        val status = input.asRightOrNull
        if (status != null) {
            ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia)
        } else {
            StatusViewData.Placeholder(false)
        }
    })

    private val statusLifter = Function<Status, Either<Placeholder, Status>> { Either.right(it) }

    enum class Kind {
        HOME,
        PUBLIC_LOCAL,
        PUBLIC_FEDERATED,
        TAG,
        USER,
        FAVOURITES,
        LIST
    }

    private enum class FetchEnd {
        TOP,
        BOTTOM,
        MIDDLE
    }

    override fun timelineCases(): TimelineCases? {
        return timelineCases
    }

    private object Placeholder {
        val instance = Placeholder
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val arguments = arguments
        kind = Kind.valueOf(arguments!!.getString(KIND_ARG))
        if (kind == Kind.TAG || kind == Kind.USER || kind == Kind.LIST) {
            hashtagOrId = arguments.getString(HASHTAG_OR_ID_ARG)
        }

        val rootView = inflater.inflate(R.layout.fragment_timeline, container, false)

        // Setup the SwipeRefreshLayout.
        val context = context
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener(this)
        swipeRefreshLayout.setColorSchemeResources(R.color.primary)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(context!!, android.R.attr.colorBackground))
        // Setup the RecyclerView.
        recyclerView = rootView.findViewById(R.id.recycler_view)
        recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        val divider = DividerItemDecoration(
                context, layoutManager.orientation)
        val drawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark)
        divider.setDrawable(drawable)
        recyclerView.addItemDecoration(divider)
        adapter = TimelineAdapter(this)
        val preferences = PreferenceManager.getDefaultSharedPreferences(
                activity)
        preferences.registerOnSharedPreferenceChangeListener(this)
        alwaysShowSensitiveMedia = preferences.getBoolean("alwaysShowSensitiveMedia", false)
        val mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true)
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled)
        recyclerView.adapter = adapter

        var filter = preferences.getBoolean("tabFilterHomeReplies", true)
        filterRemoveReplies = kind == Kind.HOME && !filter

        filter = preferences.getBoolean("tabFilterHomeBoosts", true)
        filterRemoveReblogs = kind == Kind.HOME && !filter

        val regexFilter = preferences.getString("tabFilterRegex", "")!!
        if ((kind == Kind.HOME || kind == Kind.PUBLIC_LOCAL || kind == Kind.PUBLIC_FEDERATED)
                && !regexFilter.isEmpty()) {
            filterRemoveRegexMatcher =
                    Pattern.compile(regexFilter, Pattern.CASE_INSENSITIVE).matcher("")
        }

        timelineReceiver = TimelineReceiver(this, this)
        LocalBroadcastManager.getInstance(context.applicationContext)
                .registerReceiver(timelineReceiver!!, TimelineReceiver.getFilter(kind))

        statuses.clear()
        topLoading = false
        topFetches = 0
        bottomLoading = false
        bottomFetches = 0
        bottomId = null
        topId = null

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (jumpToTopAllowed()) {
            val layout = activity!!.findViewById<TabLayout>(R.id.tab_layout)
            if (layout != null) {
                onTabSelectedListener = object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {}

                    override fun onTabUnselected(tab: TabLayout.Tab) {}

                    override fun onTabReselected(tab: TabLayout.Tab) {
                        jumpToTop()
                    }
                }
                layout.addOnTabSelectedListener(onTabSelectedListener!!)
            }
        }

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then. */
        if (actionButtonPresent()) {
            /* Use a modified scroll listener that both loads more statuses as it goes, and hides
             * the follow button on down-scroll. */
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            hideFab = preferences.getBoolean("fabHide", false)
            scrollListener = object : EndlessOnScrollListener(layoutManager) {
                override fun onScrolled(view: RecyclerView?, dx: Int, dy: Int) {
                    super.onScrolled(view, dx, dy)

                    val activity = activity as ActionButtonActivity?
                    val composeButton = activity!!.actionButton

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

                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                    this@TimelineFragment.onLoadMore()
                }
            }
        } else {
            // Just use the basic scroll listener to load more statuses.
            scrollListener = object : EndlessOnScrollListener(layoutManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                    this@TimelineFragment.onLoadMore()
                }
            }
        }
        recyclerView.addOnScrollListener(scrollListener)
        subscribeToAppStoreEvents()
    }

    override fun onDestroyView() {
        if (jumpToTopAllowed()) {
            val tabLayout = activity!!.findViewById<TabLayout>(R.id.tab_layout)
            tabLayout?.removeOnTabSelectedListener(onTabSelectedListener!!)
        }
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(timelineReceiver!!)
        super.onDestroyView()
    }

    override fun onRefresh() {
        sendFetchTimelineRequest(null, topId, FetchEnd.TOP, -1)
    }

    override fun onReply(position: Int) {
        super.reply(statuses[position].asRight)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = statuses[position].asRight
        timelineCases.reblogWithCallback(status, reblog, object : Callback<Status> {
            override fun onResponse(call: Call<Status>, response: Response<Status>) {

                if (response.isSuccessful) {
                    status.reblogged = reblog

                    if (status.reblog != null) {
                        status.reblog.reblogged = reblog
                    }

                    val actual = findStatusAndPosition(position, status) ?: return

                    val newViewData = StatusViewData.Builder(actual.first!!)
                            .setReblogged(reblog)
                            .createStatusViewData()
                    statuses.setPairedItem(actual.second!!, newViewData)
                    adapter.changeItem(actual.second!!, newViewData, false)
                    appStore.dispatch(ReblogEvent(status.id, reblog))
                }
            }

            override fun onFailure(call: Call<Status>, t: Throwable) {
                Log.d(TAG, "Failed to reblog status " + status.id, t)
            }
        })
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = statuses[position].asRight

        timelineCases.favouriteWithCallback(status, favourite, object : Callback<Status> {
            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                if (response.isSuccessful) {
                    status.favourited = favourite

                    if (status.reblog != null) {
                        status.reblog.favourited = favourite
                    }

                    val actual = findStatusAndPosition(position, status) ?: return

                    val newViewData = StatusViewData.Builder(actual.first!!)
                            .setFavourited(favourite)
                            .createStatusViewData()
                    statuses.setPairedItem(actual.second!!, newViewData)
                    adapter.changeItem(actual.second!!, newViewData, false)
                    appStore.dispatch(FavoriteEvent(status.id, favourite))
                }
            }

            override fun onFailure(call: Call<Status>, t: Throwable) {
                Log.d(TAG, "Failed to favourite status " + status.id, t)
            }
        })
    }

    override fun onMore(view: View, position: Int) {
        super.more(statuses[position].asRight, view, position)
    }

    override fun onOpenReblog(position: Int) {
        super.openReblog(statuses[position].asRight)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val newViewData = StatusViewData.Builder(
                statuses.getPairedItem(position) as StatusViewData.Concrete)
                .setIsExpanded(expanded).createStatusViewData()
        statuses.setPairedItem(position, newViewData)
        adapter.changeItem(position, newViewData, false)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val newViewData = StatusViewData.Builder(
                statuses.getPairedItem(position) as StatusViewData.Concrete)
                .setIsShowingSensitiveContent(isShowing).createStatusViewData()
        statuses.setPairedItem(position, newViewData)
        adapter.changeItem(position, newViewData, false)
    }

    override fun onLoadMore(position: Int) {
        //check bounds before accessing list,
        if (statuses.size >= position && position > 0) {
            val fromStatus = statuses[position - 1].asRightOrNull
            val toStatus = statuses[position + 1].asRightOrNull
            if (fromStatus == null || toStatus == null) {
                Log.e(TAG, "Failed to load more at $position, wrong placeholder position")
                return
            }
            sendFetchTimelineRequest(fromStatus.id, toStatus.id, FetchEnd.MIDDLE, position)

            val newViewData = StatusViewData.Placeholder(true)
            statuses.setPairedItem(position, newViewData)
            adapter.changeItem(position, newViewData, false)
        } else {
            Log.e(TAG, "error loading more")
        }
    }

    override fun onViewMedia(urls: Array<String>, urlIndex: Int, type: Attachment.Type,
                             view: View) {
        super.viewMedia(urls, urlIndex, type, view)
    }

    override fun onViewThread(position: Int) {
        super.viewThread(statuses[position].asRight)
    }

    override fun onViewTag(tag: String) {
        if (kind == Kind.TAG && hashtagOrId == tag) {
            // If already viewing a tag page, then ignore any request to view that tag again.
            return
        }
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        if (kind == Kind.USER && hashtagOrId == id) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return
        }
        super.viewAccount(id)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "fabHide" -> {
                hideFab = sharedPreferences.getBoolean("fabHide", false)
            }
            "mediaPreviewEnabled" -> {
                val enabled = sharedPreferences.getBoolean("mediaPreviewEnabled", true)
                adapter.setMediaPreviewEnabled(enabled)
                fullyRefresh()
            }
            "tabFilterHomeReplies" -> {
                val filter = sharedPreferences.getBoolean("tabFilterHomeReplies", true)
                val oldRemoveReplies = filterRemoveReplies
                filterRemoveReplies = kind == Kind.HOME && !filter
                if (adapter.itemCount > 1 && oldRemoveReplies != filterRemoveReplies) {
                    fullyRefresh()
                }
            }
            "tabFilterHomeBoosts" -> {
                val filter = sharedPreferences.getBoolean("tabFilterHomeBoosts", true)
                val oldRemoveReblogs = filterRemoveReblogs
                filterRemoveReblogs = kind == Kind.HOME && !filter
                if (adapter.itemCount > 1 && oldRemoveReblogs != filterRemoveReblogs) {
                    fullyRefresh()
                }
            }
            "tabFilterRegex" -> {
                val oldFilterRemoveRegex = filterRemoveRegexMatcher != null
                val newFilterRemoveRegexPattern = sharedPreferences.getString("tabFilterRegex", "")
                val patternChanged = if (filterRemoveRegexMatcher != null) {
                    !newFilterRemoveRegexPattern.equals(filterRemoveRegexMatcher!!.pattern().pattern(), ignoreCase = true)
                } else {
                    !newFilterRemoveRegexPattern.isEmpty()
                }
                val filterRemoveRegex = (kind == Kind.HOME || kind == Kind.PUBLIC_LOCAL || kind == Kind.PUBLIC_FEDERATED) && !newFilterRemoveRegexPattern.isEmpty()
                if (oldFilterRemoveRegex != filterRemoveRegex || patternChanged) {
                    filterRemoveRegexMatcher = Pattern.compile(newFilterRemoveRegexPattern, Pattern.CASE_INSENSITIVE).matcher("")
                    fullyRefresh()
                }
            }
            "alwaysShowSensitiveMedia" -> {
                //it is ok if only newly loaded statuses are affected, no need to fully refresh
                alwaysShowSensitiveMedia = sharedPreferences.getBoolean("alwaysShowSensitiveMedia", false)
            }
        }
    }

    override fun removeItem(position: Int) {
        statuses.removeAt(position)
        adapter.update(statuses.pairedCopy)
    }

    override fun removeAllByAccountId(accountId: String) {
        // using iterator to safely remove items while iterating
        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next().asRightOrNull
            if (status != null && status.account.id == accountId) {
                iterator.remove()
            }
        }
        adapter.update(statuses.pairedCopy)
    }

    private fun onLoadMore() {
        sendFetchTimelineRequest(bottomId, null, FetchEnd.BOTTOM, -1)
    }

    private fun fullyRefresh() {
        adapter.clear()
        sendFetchTimelineRequest(null, null, FetchEnd.TOP, -1)
    }

    private fun jumpToTopAllowed(): Boolean {
        return kind != Kind.TAG && kind != Kind.FAVOURITES
    }

    private fun actionButtonPresent(): Boolean {
        return kind != Kind.TAG && kind != Kind.FAVOURITES
    }

    private fun jumpToTop() {
        layoutManager.scrollToPosition(0)
        recyclerView.stopScroll()
        scrollListener?.reset()
    }

    private fun getFetchCallByTimelineType(kind: Kind, tagOrId: String?, fromId: String?,
                                           uptoId: String?): Call<List<Status>> {
        val api = mastodonApi
        return when (kind) {
            TimelineFragment.Kind.HOME -> api.homeTimeline(fromId, uptoId, null)
            TimelineFragment.Kind.PUBLIC_FEDERATED -> api.publicTimeline(null, fromId, uptoId, LOAD_AT_ONCE)
            TimelineFragment.Kind.PUBLIC_LOCAL -> api.publicTimeline(true, fromId, uptoId, LOAD_AT_ONCE)
            TimelineFragment.Kind.TAG -> api.hashtagTimeline(tagOrId, null, fromId, uptoId, LOAD_AT_ONCE)
            TimelineFragment.Kind.USER -> api.accountStatuses(tagOrId, fromId, uptoId, LOAD_AT_ONCE, null)
            TimelineFragment.Kind.FAVOURITES -> api.favourites(fromId, uptoId, LOAD_AT_ONCE)
            TimelineFragment.Kind.LIST -> api.listTimeline(tagOrId, fromId, uptoId, LOAD_AT_ONCE)
        }
    }

    private fun sendFetchTimelineRequest(fromId: String?, uptoId: String?,
                                         fetchEnd: FetchEnd, pos: Int) {
        /* If there is a fetch already ongoing, record however many fetches are requested and
         * fulfill them after it's complete. */
        if (fetchEnd == FetchEnd.TOP && topLoading) {
            topFetches++
            return
        }
        if (fetchEnd == FetchEnd.BOTTOM && bottomLoading) {
            bottomFetches++
            return
        }

        if (fromId != null || adapter.itemCount <= 1) {
            /* When this is called by the EndlessScrollListener it cannot refresh the footer state
             * using adapter.notifyItemChanged. So its necessary to postpone doing so until a
             * convenient time for the UI thread using a Runnable. */
            recyclerView.post { adapter.setFooterState(FooterViewHolder.State.LOADING) }
        }

        val callback = object : Callback<List<Status>> {
            override fun onResponse(call: Call<List<Status>>, response: Response<List<Status>>) {
                if (response.isSuccessful) {
                    val linkHeader = response.headers().get("Link")
                    onFetchTimelineSuccess(response.body()!!.toMutableList(), linkHeader, fetchEnd, pos)
                } else {
                    onFetchTimelineFailure(Exception(response.message()), fetchEnd, pos)
                }
            }

            override fun onFailure(call: Call<List<Status>>, t: Throwable) {
                onFetchTimelineFailure(t as Exception, fetchEnd, pos)
            }
        }

        val listCall = getFetchCallByTimelineType(kind, hashtagOrId, fromId, uptoId)
        callList.add(listCall)
        listCall.enqueue(callback)
    }

    private fun onFetchTimelineSuccess(statuses: MutableList<Status>, linkHeader: String?,
                                       fetchEnd: FetchEnd, pos: Int) {
        // We filled the hole (or reached the end) if the server returned less statuses than we
        // we asked for.
        val fullFetch = statuses.size >= LOAD_AT_ONCE
        filterStatuses(statuses)
        val links = HttpHeaderLink.parse(linkHeader)
        when (fetchEnd) {
            TimelineFragment.FetchEnd.TOP -> {
                val previous = HttpHeaderLink.findByRelationType(links, "prev")
                var uptoId: String? = null
                if (previous != null) {
                    uptoId = previous.uri.getQueryParameter("since_id")
                }
                updateStatuses(statuses, null, uptoId, fullFetch)
            }
            TimelineFragment.FetchEnd.MIDDLE -> {
                replacePlaceholderWithStatuses(statuses, fullFetch, pos)
            }
            TimelineFragment.FetchEnd.BOTTOM -> {
                val next = HttpHeaderLink.findByRelationType(links, "next")
                var fromId: String? = null
                if (next != null) {
                    fromId = next.uri.getQueryParameter("max_id")
                }
                if (adapter.itemCount > 1) {
                    addItems(statuses, fromId)
                } else {
                    /* If this is the first fetch, also save the id from the "previous" link and
                     * treat this operation as a refresh so the scroll position doesn't get pushed
                     * down to the end. */
                    val previous = HttpHeaderLink.findByRelationType(links, "prev")
                    var uptoId: String? = null
                    if (previous != null) {
                        uptoId = previous.uri.getQueryParameter("since_id")
                    }
                    updateStatuses(statuses, fromId, uptoId, fullFetch)
                }
            }
        }
        fulfillAnyQueuedFetches(fetchEnd)
        if (statuses.size == 0 && adapter.itemCount == 1) {
            adapter.setFooterState(FooterViewHolder.State.EMPTY)
        } else {
            adapter.setFooterState(FooterViewHolder.State.END)
        }
        swipeRefreshLayout.isRefreshing = false
    }

    private fun onFetchTimelineFailure(exception: Exception, fetchEnd: FetchEnd, position: Int) {
        swipeRefreshLayout.isRefreshing = false

        if (fetchEnd == FetchEnd.MIDDLE && !statuses[position].isRight) {
            val newViewData = StatusViewData.Placeholder(false)
            statuses.setPairedItem(position, newViewData)
            adapter.changeItem(position, newViewData, true)
        }

        Log.e(TAG, "Fetch Failure: " + exception.message)
        fulfillAnyQueuedFetches(fetchEnd)
    }

    private fun fulfillAnyQueuedFetches(fetchEnd: FetchEnd) {
        when (fetchEnd) {
            TimelineFragment.FetchEnd.BOTTOM -> {
                bottomLoading = false
                if (bottomFetches > 0) {
                    bottomFetches--
                    onLoadMore()
                }
            }
            TimelineFragment.FetchEnd.TOP -> {
                topLoading = false
                if (topFetches > 0) {
                    topFetches--
                    onRefresh()
                }
            }
            else -> {
                // No-op
            }
        }
    }

    private fun filterStatuses(statuses: MutableList<Status>) {
        val it = statuses.iterator()
        while (it.hasNext()) {
            val (_, _, _, inReplyToId, _, reblog, content, _, _, _, _, _, _, _, spoilerText) = it.next()
            if (inReplyToId != null && filterRemoveReplies
                    || reblog != null && filterRemoveReblogs
                    || ((filterRemoveRegexMatcher?.reset(content)?.find() == true)
                            || spoilerText.isNotEmpty()
                            && filterRemoveRegexMatcher?.reset(content)?.find() == true)) {
                it.remove()
            }
        }
    }

    private fun updateStatuses(newStatuses: List<Status>, fromId: String?,
                               toId: String?, fullFetch: Boolean) {
        if (ListUtils.isEmpty(newStatuses)) {
            return
        }
        if (fromId != null) {
            bottomId = fromId
        }
        if (toId != null) {
            topId = toId
        }

        val liftedNew = listStatusList(newStatuses)

        if (statuses.isEmpty()) {
            statuses.addAll(liftedNew)
        } else {
            val lastOfNew = liftedNew[newStatuses.size - 1]
            val index = statuses.indexOf(lastOfNew)

            for (i in 0 until index) {
                statuses.removeAt(0)
            }
            val newIndex = liftedNew.indexOf(statuses[0])
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    liftedNew.add(Either.left(Placeholder.instance))
                }
                statuses.addAll(0, liftedNew)
            } else {
                statuses.addAll(0, liftedNew.subList(0, newIndex))
            }
        }
        adapter.update(statuses.pairedCopy)
    }

    private fun addItems(newStatuses: List<Status>, fromId: String?) {
        if (ListUtils.isEmpty(newStatuses)) {
            return
        }
        val end = statuses.size
        val last = statuses[end - 1].asRightOrNull
        // I was about to replace findStatus with indexOf but it is incorrect to compare value
        // types by ID anyway and we should change equals() for Status, I think, so this makes sense
        if (last != null && !findStatus(newStatuses, last.id)) {
            statuses.addAll(listStatusList(newStatuses))
            val newViewDatas = statuses.pairedCopy
                    .subList(statuses.size - newStatuses.size, statuses.size)
            if (BuildConfig.DEBUG && newStatuses.size != newViewDatas.size) {
                val error = String.format(Locale.getDefault(),
                        "Incorrectly got statusViewData sublist." + " newStatuses.size == %d newViewDatas.size == %d, statuses.size == %d",
                        newStatuses.size, newViewDatas.size, statuses.size)
                throw AssertionError(error)
            }
            if (fromId != null) {
                bottomId = fromId
            }
            adapter.addItems(newViewDatas)
        }
    }

    private fun replacePlaceholderWithStatuses(newStatuses: List<Status>, fullFetch: Boolean, pos: Int) {
        val status = statuses[pos].asRightOrNull
        if (status == null) {
            statuses.removeAt(pos)
        }

        if (ListUtils.isEmpty(newStatuses)) {
            adapter.update(statuses.pairedCopy)
            return
        }

        val liftedNew = listStatusList(newStatuses)

        if (fullFetch) {
            liftedNew.add(Either.left(Placeholder.instance))
        }

        statuses.addAll(pos, liftedNew)
        adapter.update(statuses.pairedCopy)

    }

    private fun subscribeToAppStoreEvents() {
        appStore.events.observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(view!!.scope())
                .subscribe { event ->
                    when (event) {
                        is FavoriteEvent -> handleFavoriteEvent(event)
                        is ReblogEvent -> handleReblogEvent(event)
                    }
                }
    }

    private fun handleFavoriteEvent(event: FavoriteEvent) {
        val (statusId, fav) = event
        statuses.statusWithId(statusId)?.let { (pos, status) ->
            status.favourited = fav
            statuses[pos] = Either.right(status)
            adapter.changeItem(pos, statuses.getPairedItem(pos), true)
        }
    }

    private fun handleReblogEvent(event: ReblogEvent) {
        val (statusId, reblog) = event
        statuses.statusWithId(statusId)?.let { (pos, status) ->
            status.reblogged = reblog
            statuses[pos] = Either.right(status)
            adapter.changeItem(pos, statuses.getPairedItem(pos), true)
        }
    }

    private fun findStatusAndPosition(position: Int, status: Status): Pair<StatusViewData.Concrete, Int>? {
        val statusToUpdate: StatusViewData.Concrete
        val positionToUpdate: Int
        val someOldViewData = statuses.getPairedItem(position)

        // Unlikely, but data could change between the request and response
        if (someOldViewData is StatusViewData.Placeholder || (someOldViewData as StatusViewData.Concrete).id != status.id) {
            // try to find the status we need to update
            val foundPos = statuses.indexOf(Either.right(status))
            if (foundPos < 0) return null // okay, it's hopeless, give up
            statusToUpdate = statuses.getPairedItem(foundPos) as StatusViewData.Concrete
            positionToUpdate = position
        } else {
            statusToUpdate = someOldViewData
            positionToUpdate = position
        }
        return Pair(statusToUpdate, positionToUpdate)
    }

    private fun listStatusList(list: List<Status>): MutableList<Either<Placeholder, Status>> {
        return CollectionUtil.map(list, statusLifter)
    }

    private fun PairedList<Either<Placeholder, Status>, StatusViewData>.statusWithId(id: String): kotlin.Pair<Int, Status>? {
        for (i in this.indices) {
            val status = statuses[i].asRightOrNull
            if (status != null
                    && (status.id == id
                            || status.reblog != null
                            && status.reblog.id == id)) {
                return i to status
            }
        }
        return null
    }

    companion object {
        private const val TAG = "TimelineF" // logging tag
        private const val KIND_ARG = "kind"
        private const val HASHTAG_OR_ID_ARG = "hashtag_or_id"

        private const val LOAD_AT_ONCE = 30

        @JvmStatic
        fun newInstance(kind: Kind): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle()
            arguments.putString(KIND_ARG, kind.name)
            fragment.arguments = arguments
            return fragment
        }

        @JvmStatic
        fun newInstance(kind: Kind, hashtagOrId: String?): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle()
            arguments.putString(KIND_ARG, kind.name)
            arguments.putString(HASHTAG_OR_ID_ARG, hashtagOrId)
            fragment.arguments = arguments
            return fragment
        }

        private fun findStatus(statuses: List<Status>, id: String): Boolean {
            for ((id1) in statuses) {
                if (id1 == id) {
                    return true
                }
            }
            return false
        }
    }
}
