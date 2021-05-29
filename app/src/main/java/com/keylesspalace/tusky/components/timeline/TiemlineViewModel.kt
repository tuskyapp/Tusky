package com.keylesspalace.tusky.components.timeline

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.*
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
    private val mastodonApi: MastodonApi,
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
    private var isNeedRefresh = false
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
        val statuses = try {
            timelineRepo.getStatuses(
                maxId = topId,
                sinceId = null,
                sincedIdMinusOne = null,
                limit = LOAD_AT_ONCE,
                requestMode = TimelineRequestMode.NETWORK
            ).await()
        } catch (t: Exception) {
            if (isExpectedRequestException(t)) {
                Log.d(TAG, "Failed updating timeline", t)
                // Indicate that we are not loading anymore
                initialUpdateFailed = true
                this.isLoadingInitially = false
                this.isRefreshing = false
                this.bottomLoading = false
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
                val id = if (item is StatusViewData.Concrete) item.id
                else (item as StatusViewData.Placeholder).id

                id.isLessThan(topId)
            }
            this.statuses.addAll(mutableStatuses.map {
                if (it.isLeft()) {
                    StatusViewData.Placeholder(it.asLeft().id, false)
                } else {
                    ViewDataUtils.statusToViewData(
                        it.asRight(),
                        this.alwaysShowSensitiveMedia,
                        this.alwaysOpenSpoilers
                    )
                }
            })
        }
        bottomLoading = false
        triggerViewUpdate()
    }

    private fun isExpectedRequestException(t: Exception) = t is IOException || t is HttpException

    fun refresh(): Job {
        return viewModelScope.launch {
            isNeedRefresh = false
            failure = null
            if (initialUpdateFailed) updateCurrent()
            loadAbove()
        }
    }

    fun loadMore(): Job {
        return viewModelScope.launch {
            if (didLoadEverythingBottom || bottomLoading) {
                return@launch
            }
            if (statuses.isEmpty()) {
                loadInitial()
                return@launch
            }
            bottomLoading = true
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

            val bottomId: String? =
                if (kind == Kind.FAVOURITES || kind == Kind.BOOKMARKS) {
                    nextId
                } else {
                    statuses.lastOrNull { it is StatusViewData.Concrete }
                        ?.let { (it as StatusViewData.Concrete).id }
                }

            loadBelow(bottomId)
        }
    }

    fun loadGap(position: Int): Job {
        return viewModelScope.launch {
            //check bounds before accessing list,
            if (statuses.size >= position && position > 0) {
                val fromStatus = statuses[position - 1].asStatusOrNull()
                val toStatus = statuses[position + 1].asStatusOrNull()
                val maxMinusOne = statuses.getOrNull(position + 2)?.asStatusOrNull()?.id
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

                sendFetchTimelineRequest(
                    fromStatus.id, toStatus.id, maxMinusOne,
                    FetchEnd.MIDDLE, position
                )
            } else {
                Log.e(TAG, "error loading more")
            }
        }
    }

    fun reblog(reblog: Boolean, position: Int) {
        val status = statuses[position].asStatusOrNull() ?: return
        timelineCases.reblog(status.id, reblog)
            .subscribe({ newStatus -> updateWithNewStatus(newStatus) }) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to reblog status " + status.id,
                    t
                )
            }
            .autoDispose()
    }

    fun favorite(favorite: Boolean, position: Int) {
        val status = statuses[position].asStatusOrNull() ?: return
        timelineCases.favourite(status.id, favorite)
            .subscribe(
                { newStatus -> updateWithNewStatus(newStatus) },
                { t: Throwable? -> Log.d(TAG, "Failed to favourite status " + status.id, t) }
            )
            .autoDispose()
    }

    fun bookmark(bookmark: Boolean, position: Int) {
        val status = statuses[position].asStatusOrNull() ?: return
        timelineCases.bookmark(status.id, bookmark)
            .subscribe(
                { newStatus -> updateWithNewStatus(newStatus) },
                { t: Throwable? -> Log.d(TAG, "Failed to favourite status " + status.id, t) }
            )
            .autoDispose()
    }

    fun voteInPoll(position: Int, choices: List<Int>) {
        val status = statuses[position].asStatusOrNull() ?: return
        val poll = status.status.poll ?: run {
            Log.w(TAG, "No poll on status ${status.id}")
            return
        }

        val votedPoll = status.actionable.poll!!.votedCopy(choices)
        updatePoll(status, votedPoll)
        timelineCases.voteInPoll(status.id, poll.id, choices)
            .subscribe(
                { newPoll: Poll -> updatePoll(status, newPoll) },
                { t: Throwable? -> Log.d(TAG, "Failed to vote in poll: " + status.id, t) }
            )
            .autoDispose()
    }

    private fun updatePoll(
        status: StatusViewData.Concrete,
        newPoll: Poll
    ) {
        updateStatusById(status.id) {
            it.copy(status = it.status.copy(poll = newPoll))
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

    fun removeAllByAccountId(accountId: String) {
        statuses.removeAll { vm ->
            val status = vm.asStatusOrNull()?.status ?: return@removeAll false
            status.account.id == accountId || status.actionableStatus.account.id == accountId
        }
    }

    fun removeAllByInstance(instance: String) {
        statuses.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            LinkHelper.getDomain(status.account.url) == instance
        }
    }

    private fun triggerViewUpdate() {
        this.updateViewSubject.onNext(Unit)
    }

    private suspend fun sendFetchTimelineRequest(
        maxId: String?,
        sinceId: String?,
        sinceIdMinusOne: String?,
        fetchEnd: FetchEnd,
        pos: Int
    ) {
        if (fetchEnd == FetchEnd.TOP ||
            fetchEnd == FetchEnd.BOTTOM && maxId == null && !this.isLoadingInitially
        ) {
            this.isRefreshing = true
            triggerViewUpdate()
        }
        if (kind == Kind.HOME) {
            // allow getting old statuses/fallbacks for network only for for bottom loading
            val mode = if (fetchEnd == FetchEnd.BOTTOM) {
                TimelineRequestMode.ANY
            } else {
                TimelineRequestMode.NETWORK
            }
            try {
                val result =
                    timelineRepo.getStatuses(maxId, sinceId, sinceIdMinusOne, LOAD_AT_ONCE, mode)
                        .await()
                onFetchTimelineSuccess(result.toMutableList(), fetchEnd, pos)
            } catch (t: Exception) {
                if (isExpectedRequestException(t)) {
                    onFetchTimelineFailure(t, fetchEnd, pos)
                } else {
                    throw t
                }
            }
        } else {
            try {
                val response = getFetchCallByTimelineType(maxId, sinceId).await()
                if (response.isSuccessful) {
                    val newNextId = extractNextId(response)
                    if (newNextId != null) {
                        // when we reach the bottom of the list, we won't have a new link. If
                        // we blindly write `null` here we will start loading from the top
                        // again.
                        nextId = newNextId
                    }
                    onFetchTimelineSuccess(
                        response.body()!!.mapTo(mutableListOf()) { Either.Right(it) },
                        fetchEnd,
                        pos
                    )
                } else {
                    onFetchTimelineFailure(Exception(response.message()), fetchEnd, pos)
                }
            } catch (t: Exception) {
                onFetchTimelineFailure(t, fetchEnd, pos)
            }
        }
    }

    private fun updateWithNewStatus(newStatus: Status) {
        updateStatusById(newStatus.id) { it.copy(status = newStatus) }
    }

    private fun onFetchTimelineSuccess(
        statuses: MutableList<Either<Placeholder, Status>>,
        fetchEnd: FetchEnd, pos: Int
    ) {

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
                if (this.statuses.isNotEmpty()
                    && this.statuses[this.statuses.size - 1] !is StatusViewData.Concrete
                ) {
                    this.statuses.removeAt(this.statuses.size - 1)
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

        updateBottomLoadingState(fetchEnd)
        this.isLoadingInitially = false
        this.isRefreshing = false
        this.triggerViewUpdate()
    }

    private fun onFetchTimelineFailure(throwable: Throwable, fetchEnd: FetchEnd, position: Int) {
        this.isRefreshing = false
        if (fetchEnd == FetchEnd.MIDDLE && statuses[position] is StatusViewData.Placeholder) {
            updatePlacesholderAt(position) { it.copy(isLoading = false) }
        } else if (statuses.isEmpty()) {
            this.isRefreshing = false
            this.failure =
                if (throwable is IOException) FailureReason.NETWORK else FailureReason.OTHER
        }
        Log.e(TAG, "Fetch Failure: " + throwable.message)
        updateBottomLoadingState(fetchEnd)
        this.isLoadingInitially = false
        this.triggerViewUpdate()
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


    private fun updateBottomLoadingState(fetchEnd: FetchEnd) {
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false
        }
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
        return status.inReplyToId != null && filterRemoveReplies
                || status.reblog != null && filterRemoveReblogs
                || filterModel.shouldFilterStatus(status.actionableStatus)
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
            this.statuses.addAll(statuses.toViewData())

            this.isLoadingInitially = false
            this.triggerViewUpdate()
            // Request statuses including current top to refresh all of them
        }

        updateCurrent()
        loadAbove().join()
    }

    fun loadInitial(): Job {
        this.isLoadingInitially = true
        this.bottomLoading = true
        this.triggerViewUpdate()
        return viewModelScope.launch {
            if (kind == Kind.HOME) {
                tryCache()
            } else {
                loadBelow(null)
            }
        }
    }

    fun loadAbove(): Job {
        return viewModelScope.launch {
            var firstOrNull: String? = null
            var secondOrNull: String? = null
            for (i in statuses.indices) {
                val status = statuses[i].asStatusOrNull() ?: continue
                firstOrNull = status.id
                secondOrNull = statuses.getOrNull(i + 1)?.asStatusOrNull()?.id
                break
            }

            if (firstOrNull != null) {
                sendFetchTimelineRequest(
                    maxId = null,
                    sinceId = firstOrNull,
                    sinceIdMinusOne = secondOrNull,
                    fetchEnd = FetchEnd.TOP,
                    pos = -1
                )
            } else {
                loadBelow(null)
            }
        }
    }

    fun fullyRefresh(): Job {
        this.statuses.clear()
        return loadInitial()
    }

    private fun getFetchCallByTimelineType(
        fromId: String?,
        uptoId: String?
    ): Single<Response<List<Status>>> {
        val api = mastodonApi
        return when (kind) {
            Kind.HOME -> api.homeTimeline(
                fromId, uptoId,
                LOAD_AT_ONCE
            )
            Kind.PUBLIC_FEDERATED -> api.publicTimeline(
                null,
                fromId,
                uptoId,
                LOAD_AT_ONCE
            )
            Kind.PUBLIC_LOCAL -> api.publicTimeline(
                true,
                fromId,
                uptoId,
                LOAD_AT_ONCE
            )
            Kind.TAG -> {
                val firstHashtag = tags[0]
                val additionalHashtags = tags.subList(1, tags.size)
                api.hashtagTimeline(
                    firstHashtag,
                    additionalHashtags,
                    null,
                    fromId,
                    uptoId,
                    LOAD_AT_ONCE
                )
            }
            Kind.USER -> api.accountStatuses(
                id!!,
                fromId,
                uptoId,
                LOAD_AT_ONCE,
                true,
                null,
                null
            )
            Kind.USER_PINNED -> api.accountStatuses(
                id!!,
                fromId,
                uptoId,
                LOAD_AT_ONCE,
                null,
                null,
                true
            )
            Kind.USER_WITH_REPLIES -> api.accountStatuses(
                id!!,
                fromId,
                uptoId,
                LOAD_AT_ONCE,
                null,
                null,
                null
            )
            Kind.FAVOURITES -> api.favourites(
                fromId, uptoId,
                LOAD_AT_ONCE
            )
            Kind.BOOKMARKS -> api.bookmarks(
                fromId, uptoId,
                LOAD_AT_ONCE
            )
            Kind.LIST -> api.listTimeline(
                id!!, fromId, uptoId,
                LOAD_AT_ONCE
            )
        }
    }

    private fun replacePlaceholderWithStatuses(
        newStatuses: MutableList<Either<Placeholder, Status>>,
        fullFetch: Boolean, pos: Int
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
        updateStatusById(reblogEvent.statusId) {
            it.copy(status = it.status.copy(reblogged = reblogEvent.reblog))
        }
    }

    private fun handleFavEvent(favEvent: FavoriteEvent) {
        updateStatusById(favEvent.statusId) {
            it.copy(status = it.status.copy(favourited = favEvent.favourite))
        }
    }

    private fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        updateStatusById(bookmarkEvent.statusId) {
            it.copy(status = it.status.copy(bookmarked = bookmarkEvent.bookmark))
        }
    }

    private fun handlePinEvent(pinEvent: PinEvent) {
        updateStatusById(pinEvent.statusId) {
            it.copy(status = it.status.copy(pinned = pinEvent.pinned))
        }
    }

    private suspend fun handleStatusComposeEvent(status: Status) {
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

    private suspend fun handleEvent(event: Event) {
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

    private suspend fun loadBelow(bottomId: String?) {
        sendFetchTimelineRequest(
            maxId = bottomId,
            sinceId = null,
            sinceIdMinusOne = null,
            fetchEnd = FetchEnd.BOTTOM,
            pos = -1
        )
    }

    private inline fun updateStatusById(
        id: String,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val pos = statuses.indexOfFirst { it.asStatusOrNull()?.id == id }
        if (pos == -1) return
        updateStatusAt(pos, updater)
    }

    private inline fun updateStatusAt(
        position: Int,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val status = statuses.getOrNull(position)?.asStatusOrNull() ?: return
        statuses[position] = updater(status)
        triggerViewUpdate()
    }

    private inline fun updatePlacesholderAt(
        position: Int,
        updater: (StatusViewData.Placeholder) -> StatusViewData.Placeholder
    ) {
        val placeholder = statuses.getOrNull(position)?.asPlaceholderOrNull() ?: return
        statuses[position] = updater(placeholder)
    }

    private fun List<TimelineStatus>.toViewData(): List<StatusViewData> = this.map {
        if (it.isRight()) {
            ViewDataUtils.statusToViewData(
                it.asRight(),
                alwaysShowSensitiveMedia,
                alwaysOpenSpoilers
            )
        } else {
            StatusViewData.Placeholder(it.asLeft().id, false)
        }
    }

    private fun reloadFilters() {
        viewModelScope.launch {
            val filters = try {
                mastodonApi.getFilters().await()
            } catch (t: Exception) {
                Log.e(TAG, "Failed to fetch filters", t)
                return@launch
            }
            filterModel.initWithFilters(filters.filter {
                filterContextMatchesKind(kind, it.context)
            })
            filterViewData(this@TimelineViewModel.statuses)
        }
    }

    companion object {
        private const val TAG = "TimelineVM"
        internal const val LOAD_AT_ONCE = 30
    }

    enum class Kind {
        HOME, PUBLIC_LOCAL, PUBLIC_FEDERATED, TAG, USER, USER_PINNED, USER_WITH_REPLIES, FAVOURITES, LIST, BOOKMARKS
    }

    enum class FetchEnd {
        TOP, BOTTOM, MIDDLE
    }
}