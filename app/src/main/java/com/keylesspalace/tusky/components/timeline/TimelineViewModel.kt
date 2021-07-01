package com.keylesspalace.tusky.components.timeline

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.DomainMuteEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.MuteConversationEvent
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.LinkHelper
import com.keylesspalace.tusky.util.RxAwareViewModel
import com.keylesspalace.tusky.util.dec
import com.keylesspalace.tusky.util.firstIsInstanceOrNull
import com.keylesspalace.tusky.util.inc
import com.keylesspalace.tusky.util.isLessThan
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

class TimelineViewModel @Inject constructor(
    private val timelineRepo: TimelineRepository,
    private val timelineCases: TimelineCases,
    private val api: MastodonApi,
    private val eventHub: EventHub,
    private val accountManager: AccountManager,
    private val sharedPreferences: SharedPreferences,
    private val filterModel: FilterModel,
) : RxAwareViewModel() {

    enum class FailureReason {
        NETWORK,
        OTHER,
    }

    val viewUpdates: Observable<Unit>
        get() = updateViewSubject

    var kind: Kind = Kind.HOME
        private set

    var isLoadingInitially = false
        private set
    var isRefreshing = false
        private set
    var bottomLoading = false
        private set
    var initialUpdateFailed = false
        private set
    var failure: FailureReason? = null
        private set
    var id: String? = null
        private set
    var tags: List<String> = emptyList()
        private set

    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoilers = false
    private var filterRemoveReplies = false
    private var filterRemoveReblogs = false
    private var didLoadEverythingBottom = false

    private var updateViewSubject = PublishSubject.create<Unit>()

    /**
     * For some timeline kinds we must use LINK headers and not just status ids.
     */
    private var nextId: String? = null

    val statuses = mutableListOf<StatusViewData>()

    fun init(
        kind: Kind,
        id: String?,
        tags: List<String>
    ) {
        this.kind = kind
        this.id = id
        this.tags = tags

        if (kind == Kind.HOME) {
            filterRemoveReplies =
                !sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
            filterRemoveReblogs =
                !sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
        }
        this.alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        this.alwaysOpenSpoilers = accountManager.activeAccount!!.alwaysOpenSpoiler

        viewModelScope.launch {
            eventHub.events
                .asFlow()
                .collect { event -> handleEvent(event) }
        }

        reloadFilters()
    }

    private suspend fun updateCurrent() {
        val topId = statuses.firstIsInstanceOrNull<StatusViewData.Concrete>()?.id ?: return
        // Request statuses including current top to refresh all of them
        val topIdMinusOne = topId.inc()
        val statuses = try {
            loadStatuses(
                maxId = topIdMinusOne,
                sinceId = null,
                sinceIdMinusOne = null,
                TimelineRequestMode.NETWORK,
            )
        } catch (t: Exception) {
            initialUpdateFailed = true
            if (isExpectedRequestException(t)) {
                Log.d(TAG, "Failed updating timeline", t)
                triggerViewUpdate()
                return
            } else {
                throw t
            }
        }

        initialUpdateFailed = false

        // When cached timeline is too old, we would replace it with nothing
        if (statuses.isNotEmpty()) {
            val mutableStatuses = statuses.toMutableList()
            filterStatuses(mutableStatuses)
            this.statuses.removeAll { item ->
                val id = when (item) {
                    is StatusViewData.Concrete -> item.id
                    is StatusViewData.Placeholder -> item.id
                }

                id == topId || id.isLessThan(topId)
            }
            this.statuses.addAll(mutableStatuses.toViewData())
        }
        triggerViewUpdate()
    }

    private fun isExpectedRequestException(t: Exception) = t is IOException || t is HttpException

    fun refresh(): Job {
        return viewModelScope.launch {
            isRefreshing = true
            failure = null
            triggerViewUpdate()

            try {
                if (initialUpdateFailed) updateCurrent()
                loadAbove()
            } catch (e: Exception) {
                if (isExpectedRequestException(e)) {
                    Log.e(TAG, "Failed to refresh", e)
                } else {
                    throw e
                }
            } finally {
                isRefreshing = false
                triggerViewUpdate()
            }
        }
    }

    /** When reaching the end of list. WIll optionally show spinner in the end of list. */
    fun loadMore(): Job {
        return viewModelScope.launch {
            if (didLoadEverythingBottom || bottomLoading) {
                return@launch
            }
            if (statuses.isEmpty()) {
                loadInitial().join()
                return@launch
            }
            setLoadingPlaceholderBelow()

            val bottomId: String? =
                if (kind == Kind.FAVOURITES || kind == Kind.BOOKMARKS) {
                    nextId
                } else {
                    statuses.lastOrNull { it is StatusViewData.Concrete }
                        ?.let { (it as StatusViewData.Concrete).id }
                }
            try {
                loadBelow(bottomId)
            } catch (e: Exception) {
                if (isExpectedRequestException(e)) {
                    if (statuses.lastOrNull() is StatusViewData.Placeholder) {
                        statuses.removeAt(statuses.lastIndex)
                    }
                } else {
                    throw e
                }
            } finally {
                triggerViewUpdate()
            }
        }
    }

    /** Load and insert statuses below the [bottomId]. Does not indicate progress. */
    private suspend fun loadBelow(bottomId: String?) {
        this.bottomLoading = true
        try {
            val statuses = loadStatuses(
                bottomId,
                null,
                null,
                TimelineRequestMode.ANY
            )
            addStatusesBelow(statuses.toMutableList())
        } finally {
            this.bottomLoading = false
        }
    }

    private fun setLoadingPlaceholderBelow() {
        val last = statuses.last()
        val placeholder: StatusViewData.Placeholder
        if (last is StatusViewData.Concrete) {
            val placeholderId = last.id.dec()
            placeholder = StatusViewData.Placeholder(placeholderId, true)
            statuses.add(placeholder)
        } else {
            placeholder = last as StatusViewData.Placeholder
        }
        statuses[statuses.lastIndex] = placeholder
        triggerViewUpdate()
    }

    private fun addStatusesBelow(statuses: MutableList<Either<Placeholder, Status>>) {
        val fullFetch = isFullFetch(statuses)
        // Remove placeholder in the bottom if it's there
        if (this.statuses.isNotEmpty() &&
            this.statuses.last() !is StatusViewData.Concrete
        ) {
            this.statuses.removeAt(this.statuses.lastIndex)
        }

        // Removing placeholder if it's the last one from the cache
        if (statuses.isNotEmpty() && !statuses[statuses.size - 1].isRight()) {
            statuses.removeAt(statuses.size - 1)
        }

        val oldSize = this.statuses.size
        if (this.statuses.isNotEmpty()) {
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

    fun loadGap(position: Int): Job {
        return viewModelScope.launch {
            // check bounds before accessing list,
            if (statuses.size < position || position <= 0) {
                Log.e(TAG, "Wrong gap position: $position")
                return@launch
            }

            val fromStatus = statuses[position - 1].asStatusOrNull()
            val toStatus = statuses[position + 1].asStatusOrNull()
            val toMinusOne = statuses.getOrNull(position + 2)?.asStatusOrNull()?.id
            if (fromStatus == null || toStatus == null) {
                Log.e(TAG, "Failed to load more at $position, wrong placeholder position")
                return@launch
            }
            val placeholder = statuses[position].asPlaceholderOrNull() ?: run {
                Log.e(TAG, "Not a placeholder at $position")
                return@launch
            }

            val newViewData: StatusViewData = StatusViewData.Placeholder(placeholder.id, true)
            statuses[position] = newViewData
            triggerViewUpdate()

            try {
                val statuses = loadStatuses(
                    fromStatus.id,
                    toStatus.id,
                    toMinusOne,
                    TimelineRequestMode.NETWORK
                )
                replacePlaceholderWithStatuses(
                    statuses.toMutableList(),
                    isFullFetch(statuses),
                    position
                )
            } catch (t: Exception) {
                if (isExpectedRequestException(t)) {
                    Log.e(TAG, "Failed to load gap", t)
                    if (statuses[position] is StatusViewData.Placeholder) {
                        statuses[position] = StatusViewData.Placeholder(placeholder.id, false)
                    }
                } else {
                    throw t
                }
            }
        }
    }

    fun reblog(reblog: Boolean, position: Int): Job = viewModelScope.launch {
        val status = statuses[position].asStatusOrNull() ?: return@launch
        try {
            timelineCases.reblog(status.actionableId, reblog).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to reblog status " + status.actionableId, t)
            }
        }
    }

    fun favorite(favorite: Boolean, position: Int): Job = viewModelScope.launch {
        val status = statuses[position].asStatusOrNull() ?: return@launch

        try {
            timelineCases.favourite(status.actionableId, favorite).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun bookmark(bookmark: Boolean, position: Int): Job = viewModelScope.launch {
        val status = statuses[position].asStatusOrNull() ?: return@launch
        try {
            timelineCases.bookmark(status.actionableId, bookmark).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun voteInPoll(position: Int, choices: List<Int>): Job = viewModelScope.launch {
        val status = statuses[position].asStatusOrNull() ?: return@launch

        val poll = status.actionable.poll ?: run {
            Log.w(TAG, "No poll on status ${status.id}")
            return@launch
        }

        val votedPoll = poll.votedCopy(choices)
        updatePoll(status, votedPoll)

        try {
            timelineCases.voteInPoll(status.actionableId, poll.id, choices).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to vote in poll: " + status.actionableId, t)
            }
        }
    }

    private fun updatePoll(
        status: StatusViewData.Concrete,
        newPoll: Poll
    ) {
        updateActionableStatusById(status.id) {
            it.copy(poll = newPoll)
        }
    }

    fun changeExpanded(expanded: Boolean, position: Int) {
        updateStatusAt(position) { it.copy(isExpanded = expanded) }
        triggerViewUpdate()
    }

    fun changeContentHidden(isShowing: Boolean, position: Int) {
        updateStatusAt(position) { it.copy(isShowingContent = isShowing) }
        triggerViewUpdate()
    }

    fun changeContentCollapsed(isCollapsed: Boolean, position: Int) {
        updateStatusAt(position) { it.copy(isCollapsed = isCollapsed) }
        triggerViewUpdate()
    }

    private fun removeAllByAccountId(accountId: String) {
        statuses.removeAll { vm ->
            val status = vm.asStatusOrNull()?.status ?: return@removeAll false
            status.account.id == accountId || status.actionableStatus.account.id == accountId
        }
    }

    private fun removeAllByInstance(instance: String) {
        statuses.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            LinkHelper.getDomain(status.account.url) == instance
        }
    }

    private fun triggerViewUpdate() {
        this.updateViewSubject.onNext(Unit)
    }

    private suspend fun loadStatuses(
        maxId: String?,
        sinceId: String?,
        sinceIdMinusOne: String?,
        homeMode: TimelineRequestMode,
    ): List<TimelineStatus> {
        val statuses = if (kind == Kind.HOME) {
            timelineRepo.getStatuses(maxId, sinceId, sinceIdMinusOne, LOAD_AT_ONCE, homeMode)
                .await()
        } else {
            val response = fetchStatusesForKind(maxId, sinceId, LOAD_AT_ONCE).await()
            if (response.isSuccessful) {
                val newNextId = extractNextId(response)
                if (newNextId != null) {
                    // when we reach the bottom of the list, we won't have a new link. If
                    // we blindly write `null` here we will start loading from the top
                    // again.
                    nextId = newNextId
                }
                response.body()?.map { Either.Right(it) } ?: listOf()
            } else {
                throw HttpException(response)
            }
        }.toMutableList()

        filterStatuses(statuses)

        return statuses
    }

    private fun updateStatuses(
        newStatuses: MutableList<Either<Placeholder, Status>>,
        fullFetch: Boolean
    ) {
        if (statuses.isEmpty()) {
            statuses.addAll(newStatuses.toViewData())
        } else {
            val lastOfNew = newStatuses.lastOrNull()
            val index = if (lastOfNew == null) -1
            else statuses.indexOfLast { it.asStatusOrNull()?.id === lastOfNew.asRightOrNull()?.id }
            if (index >= 0) {
                statuses.subList(0, index).clear()
            }

            val newIndex =
                newStatuses.indexOfFirst {
                    it.isRight() && it.asRight().id == (statuses[0] as? StatusViewData.Concrete)?.id
                }
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    val placeholderId =
                        newStatuses.last { status -> status.isRight() }.asRight().id.inc()
                    newStatuses.add(Either.Left(Placeholder(placeholderId)))
                }
                statuses.addAll(0, newStatuses.toViewData())
            } else {
                statuses.addAll(0, newStatuses.subList(0, newIndex).toViewData())
            }
        }
        // Remove all consecutive placeholders
        removeConsecutivePlaceholders()
        this.triggerViewUpdate()
    }

    private fun filterViewData(viewData: MutableList<StatusViewData>) {
        viewData.removeAll { vd ->
            vd.asStatusOrNull()?.status?.let { shouldFilterStatus(it) } ?: false
        }
    }

    private fun filterStatuses(statuses: MutableList<Either<Placeholder, Status>>) {
        statuses.removeAll { status ->
            status.asRightOrNull()?.let { shouldFilterStatus(it) } ?: false
        }
    }

    private fun shouldFilterStatus(status: Status): Boolean {
        return status.inReplyToId != null && filterRemoveReplies ||
            status.reblog != null && filterRemoveReblogs ||
            filterModel.shouldFilterStatus(status.actionableStatus)
    }

    private fun extractNextId(response: Response<*>): String? {
        val linkHeader = response.headers()["Link"] ?: return null
        val links = HttpHeaderLink.parse(linkHeader)
        val nextHeader = HttpHeaderLink.findByRelationType(links, "next") ?: return null
        val nextLink = nextHeader.uri ?: return null
        return nextLink.getQueryParameter("max_id")
    }

    private suspend fun tryCache() {
        // Request timeline from disk to make it quick, then replace it with timeline from
        // the server to update it
        val statuses =
            timelineRepo.getStatuses(null, null, null, LOAD_AT_ONCE, TimelineRequestMode.DISK)
                .await()

        val mutableStatusResponse = statuses.toMutableList()
        filterStatuses(mutableStatusResponse)
        if (statuses.size > 1) {
            clearPlaceholdersForResponse(mutableStatusResponse)
            this.statuses.clear()
            this.statuses.addAll(mutableStatusResponse.toViewData())
        }
    }

    fun loadInitial(): Job {
        return viewModelScope.launch {
            if (statuses.isNotEmpty() || initialUpdateFailed || isLoadingInitially) {
                return@launch
            }
            isLoadingInitially = true
            failure = null
            triggerViewUpdate()

            if (kind == Kind.HOME) {
                tryCache()
                isLoadingInitially = statuses.isEmpty()
                updateCurrent()
                try {
                    loadAbove()
                } catch (e: Exception) {
                    Log.e(TAG, "Loading above failed", e)
                    if (!isExpectedRequestException(e)) {
                        throw e
                    } else if (statuses.isEmpty()) {
                        failure =
                            if (e is IOException) FailureReason.NETWORK
                            else FailureReason.OTHER
                    }
                } finally {
                    isLoadingInitially = false
                    triggerViewUpdate()
                }
            } else {
                try {
                    loadBelow(null)
                } catch (e: IOException) {
                    failure = FailureReason.NETWORK
                } catch (e: HttpException) {
                    failure = FailureReason.OTHER
                } finally {
                    isLoadingInitially = false
                    triggerViewUpdate()
                }
            }
        }
    }

    private suspend fun loadAbove() {
        var firstOrNull: String? = null
        var secondOrNull: String? = null
        for (i in statuses.indices) {
            val status = statuses[i].asStatusOrNull() ?: continue
            firstOrNull = status.id
            secondOrNull = statuses.getOrNull(i + 1)?.asStatusOrNull()?.id
            break
        }

        try {
            if (firstOrNull != null) {
                triggerViewUpdate()

                val statuses = loadStatuses(
                    maxId = null,
                    sinceId = firstOrNull,
                    sinceIdMinusOne = secondOrNull,
                    homeMode = TimelineRequestMode.NETWORK
                )

                val fullFetch = isFullFetch(statuses)
                updateStatuses(statuses.toMutableList(), fullFetch)
            } else {
                loadBelow(null)
            }
        } finally {
            triggerViewUpdate()
        }
    }

    private fun isFullFetch(statuses: List<TimelineStatus>) = statuses.size >= LOAD_AT_ONCE

    private fun fullyRefresh(): Job {
        this.statuses.clear()
        return loadInitial()
    }

    private fun fetchStatusesForKind(
        fromId: String?,
        uptoId: String?,
        limit: Int
    ): Single<Response<List<Status>>> {
        return when (kind) {
            Kind.HOME -> api.homeTimeline(fromId, uptoId, limit)
            Kind.PUBLIC_FEDERATED -> api.publicTimeline(null, fromId, uptoId, limit)
            Kind.PUBLIC_LOCAL -> api.publicTimeline(true, fromId, uptoId, limit)
            Kind.TAG -> {
                val firstHashtag = tags[0]
                val additionalHashtags = tags.subList(1, tags.size)
                api.hashtagTimeline(firstHashtag, additionalHashtags, null, fromId, uptoId, limit)
            }
            Kind.USER -> api.accountStatuses(
                id!!,
                fromId,
                uptoId,
                limit,
                excludeReplies = true,
                onlyMedia = null,
                pinned = null
            )
            Kind.USER_PINNED -> api.accountStatuses(
                id!!,
                fromId,
                uptoId,
                limit,
                excludeReplies = null,
                onlyMedia = null,
                pinned = true
            )
            Kind.USER_WITH_REPLIES -> api.accountStatuses(
                id!!,
                fromId,
                uptoId,
                limit,
                excludeReplies = null,
                onlyMedia = null,
                pinned = null
            )
            Kind.FAVOURITES -> api.favourites(fromId, uptoId, limit)
            Kind.BOOKMARKS -> api.bookmarks(fromId, uptoId, limit)
            Kind.LIST -> api.listTimeline(id!!, fromId, uptoId, limit)
        }
    }

    private fun replacePlaceholderWithStatuses(
        newStatuses: MutableList<Either<Placeholder, Status>>,
        fullFetch: Boolean,
        pos: Int
    ) {
        val placeholder = statuses[pos]
        if (placeholder is StatusViewData.Placeholder) {
            statuses.removeAt(pos)
        }
        if (newStatuses.isEmpty()) {
            return
        }
        val newViewData = newStatuses
            .toViewData()
            .toMutableList()

        if (fullFetch) {
            newViewData.add(placeholder)
        }
        statuses.addAll(pos, newViewData)
        removeConsecutivePlaceholders()
        triggerViewUpdate()
    }

    private fun removeConsecutivePlaceholders() {
        for (i in 0 until statuses.size - 1) {
            if (statuses[i] is StatusViewData.Placeholder &&
                statuses[i + 1] is StatusViewData.Placeholder
            ) {
                statuses.removeAt(i)
            }
        }
    }

    private fun addItems(newStatuses: List<Either<Placeholder, Status>>) {
        if (newStatuses.isEmpty()) {
            return
        }
        statuses.addAll(newStatuses.toViewData())
        removeConsecutivePlaceholders()
    }

    /**
     * For certain requests we don't want to see placeholders, they will be removed some other way
     */
    private fun clearPlaceholdersForResponse(statuses: MutableList<Either<Placeholder, Status>>) {
        statuses.removeAll { status -> status.isLeft() }
    }

    private fun handleReblogEvent(reblogEvent: ReblogEvent) {
        updateActionableStatusById(reblogEvent.statusId) {
            it.copy(reblogged = reblogEvent.reblog)
        }
    }

    private fun handleFavEvent(favEvent: FavoriteEvent) {
        updateActionableStatusById(favEvent.statusId) {
            it.copy(favourited = favEvent.favourite)
        }
    }

    private fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        updateActionableStatusById(bookmarkEvent.statusId) {
            it.copy(bookmarked = bookmarkEvent.bookmark)
        }
    }

    private fun handlePinEvent(pinEvent: PinEvent) {
        updateActionableStatusById(pinEvent.statusId) {
            it.copy(pinned = pinEvent.pinned)
        }
    }

    private fun handleStatusComposeEvent(status: Status) {
        when (kind) {
            Kind.HOME, Kind.PUBLIC_FEDERATED, Kind.PUBLIC_LOCAL -> refresh()
            Kind.USER, Kind.USER_WITH_REPLIES -> if (status.account.id == id) {
                refresh()
            } else {
                return
            }
            Kind.TAG, Kind.FAVOURITES, Kind.LIST, Kind.BOOKMARKS, Kind.USER_PINNED -> return
        }
    }

    private fun deleteStatusById(id: String) {
        for (i in statuses.indices) {
            val either = statuses[i]
            if (either.asStatusOrNull()?.id == id) {
                statuses.removeAt(i)
                break
            }
        }
    }

    private fun onPreferenceChanged(key: String) {
        when (key) {
            PrefKeys.TAB_FILTER_HOME_REPLIES -> {
                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
                val oldRemoveReplies = filterRemoveReplies
                filterRemoveReplies = kind == Kind.HOME && !filter
                if (statuses.isNotEmpty() && oldRemoveReplies != filterRemoveReplies) {
                    fullyRefresh()
                }
            }
            PrefKeys.TAB_FILTER_HOME_BOOSTS -> {
                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
                val oldRemoveReblogs = filterRemoveReblogs
                filterRemoveReblogs = kind == Kind.HOME && !filter
                if (statuses.isNotEmpty() && oldRemoveReblogs != filterRemoveReblogs) {
                    fullyRefresh()
                }
            }
            Filter.HOME, Filter.NOTIFICATIONS, Filter.THREAD, Filter.PUBLIC, Filter.ACCOUNT -> {
                if (filterContextMatchesKind(kind, listOf(key))) {
                    reloadFilters()
                }
            }
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> {
                // it is ok if only newly loaded statuses are affected, no need to fully refresh
                alwaysShowSensitiveMedia =
                    accountManager.activeAccount!!.alwaysShowSensitiveMedia
            }
        }
    }

    // public for now
    fun filterContextMatchesKind(
        kind: Kind,
        filterContext: List<String>
    ): Boolean {
        // home, notifications, public, thread
        return when (kind) {
            Kind.HOME, Kind.LIST -> filterContext.contains(
                Filter.HOME
            )
            Kind.PUBLIC_FEDERATED, Kind.PUBLIC_LOCAL, Kind.TAG -> filterContext.contains(
                Filter.PUBLIC
            )
            Kind.FAVOURITES -> filterContext.contains(Filter.PUBLIC) || filterContext.contains(
                Filter.NOTIFICATIONS
            )
            Kind.USER, Kind.USER_WITH_REPLIES, Kind.USER_PINNED -> filterContext.contains(
                Filter.ACCOUNT
            )
            else -> false
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is FavoriteEvent -> handleFavEvent(event)
            is ReblogEvent -> handleReblogEvent(event)
            is BookmarkEvent -> handleBookmarkEvent(event)
            is PinEvent -> handlePinEvent(event)
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

    private inline fun updateActionableStatusById(
        id: String,
        updater: (Status) -> Status
    ) {
        val pos = statuses.indexOfFirst { it.asStatusOrNull()?.actionableId == id }
        if (pos == -1) return
        updateStatusAt(pos) {
            if (it.status.reblog != null) {
                it.copy(status = it.status.copy(reblog = updater(it.status.reblog)))
            } else {
                it.copy(status = updater(it.status))
            }
        }
    }

    private inline fun updateStatusAt(
        position: Int,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val status = statuses.getOrNull(position)?.asStatusOrNull() ?: return
        statuses[position] = updater(status)
        triggerViewUpdate()
    }

    private fun List<TimelineStatus>.toViewData(): List<StatusViewData> = this.map {
        when (it) {
            is Either.Right -> it.value.toViewData(
                alwaysShowSensitiveMedia,
                alwaysOpenSpoilers
            )
            is Either.Left -> StatusViewData.Placeholder(it.value.id, false)
        }
    }

    private fun reloadFilters() {
        viewModelScope.launch {
            val filters = try {
                api.getFilters().await()
            } catch (t: Exception) {
                Log.e(TAG, "Failed to fetch filters", t)
                return@launch
            }
            filterModel.initWithFilters(
                filters.filter {
                    filterContextMatchesKind(kind, it.context)
                }
            )
            filterViewData(this@TimelineViewModel.statuses)
        }
    }

    private inline fun ifExpected(
        t: Exception,
        cb: () -> Unit
    ) {
        if (isExpectedRequestException(t)) {
            cb()
        } else {
            throw t
        }
    }

    companion object {
        private const val TAG = "TimelineVM"
        internal const val LOAD_AT_ONCE = 30
    }

    enum class Kind {
        HOME, PUBLIC_LOCAL, PUBLIC_FEDERATED, TAG, USER, USER_PINNED, USER_WITH_REPLIES, FAVOURITES, LIST, BOOKMARKS
    }
}
