package com.keylesspalace.tusky.components.timeline

import android.util.Log
import androidx.core.util.Pair
import androidx.lifecycle.viewModelScope
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
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
import java.io.IOError
import javax.inject.Inject

class TimelineViewModel @Inject constructor(
    private val timelineRepo: TimelineRepository,
    private val timelineCases: TimelineCases,
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
) : RxAwareViewModel() {

    val viewUpdates: Observable<Unit>
        get() = updateViewSubject

    var kind: Kind = Kind.HOME

    var alwaysShowSensitiveMedia = false
    var alwaysOpenSpoilers = false

    var isLoadingInitially = false
    var isRefreshing = false
    var bottomLoading = false
    var initialUpdateFailed = false
    var isNeedRefresh = false

    private var updateViewSubject = PublishSubject.create<Unit>()
    private var didLoadEverythingBottom = false

    var id: String? = null
        private set
    var tags: List<String> = emptyList()
        private set

    /**
     * For some timeline kinds we must use LINK headers and not just status ids.
     */
    private var nextId: String? = null

    val statuses = PairedList<Either<Placeholder, Status>, StatusViewData> { input ->
        val status = input.asRightOrNull()
        if (status != null) {
            ViewDataUtils.statusToViewData(
                status,
                alwaysShowSensitiveMedia,
                alwaysOpenSpoilers
            )
        } else {
            val (id1) = input.asLeft()
            StatusViewData.Placeholder(id1, false)
        }
    }

    fun init(
        kind: Kind, id: String?, tags: List<String>, alwaysShowSensitiveMedia: Boolean,
        alwaysOpenSpoilers: Boolean
    ) {
        this.kind = kind
        this.id = id
        this.tags = tags
        this.alwaysShowSensitiveMedia = alwaysShowSensitiveMedia
        this.alwaysOpenSpoilers = alwaysOpenSpoilers

        viewModelScope.launch {
            eventHub.events
                .asFlow()
                .collect { event -> handleEvent(event) }
        }
    }

    private suspend fun updateCurrent() {
        if (statuses.isEmpty()) {
            return
        }
        val topId = statuses.first { status -> status.isRight() }!!.asRight().id
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
        }
        bottomLoading = false
        triggerViewUpdate()
    }

    private fun isExpectedRequestException(t: Exception) = t is IOError || t is HttpException

    fun refresh(): Job {
        return viewModelScope.launch {
            isNeedRefresh = false
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
            val last = statuses[statuses.size - 1]
            val placeholder: Placeholder
            if (last!!.isRight()) {
                val placeholderId = last.asRight().id.dec()
                placeholder = Placeholder(placeholderId)
                statuses.add(Either.Left(placeholder))
            } else {
                placeholder = last.asLeft()
            }
            statuses.setPairedItem(
                statuses.size - 1,
                StatusViewData.Placeholder(placeholder.id, true)
            )
            triggerViewUpdate()

            val bottomId: String? =
                if (kind == Kind.FAVOURITES || kind == Kind.BOOKMARKS) {
                    nextId
                } else {
                    statuses.lastOrNull { it.isRight() }?.asRight()?.id
                }

            loadBelow(bottomId)
        }
    }

    fun loadGap(position: Int): Job {
        return viewModelScope.launch {
            //check bounds before accessing list,
            if (statuses.size >= position && position > 0) {
                val fromStatus = statuses[position - 1].asRightOrNull()
                val toStatus = statuses[position + 1].asRightOrNull()
                val maxMinusOne = statuses.getOrNull(position + 2)?.asRightOrNull()?.id
                if (fromStatus == null || toStatus == null) {
                    Log.e(TAG, "Failed to load more at $position, wrong placeholder position")
                    return@launch
                }
                val (id1) = statuses[position].asLeft()
                val newViewData: StatusViewData = StatusViewData.Placeholder(id1, true)
                statuses.setPairedItem(position, newViewData)
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
        val status = statuses[position].asRight()
        timelineCases.reblog(status, reblog)
            .subscribe(
                { newStatus: Status -> setRebloggedForStatus(position, newStatus, reblog) }
            ) { t: Throwable? ->
                Log.d(
                    TAG,
                    "Failed to reblog status " + status.id,
                    t
                )
            }
            .autoDispose()
    }

    fun favorite(favorite: Boolean, position: Int) {
        val status = statuses[position].asRight()
        timelineCases.favourite(status, favorite)
            .subscribe(
                { newStatus: Status -> setFavoriteForStatus(position, newStatus, favorite) },
                { t: Throwable? -> Log.d(TAG, "Failed to favourite status " + status.id, t) }
            )
            .autoDispose()
    }

    fun bookmark(bookmark: Boolean, position: Int) {
        val status = statuses[position].asRight()
        timelineCases.bookmark(status, bookmark)
            .subscribe(
                { newStatus: Status -> setBookmarkForStatus(position, newStatus, bookmark) },
                { t: Throwable? -> Log.d(TAG, "Failed to favourite status " + status.id, t) }
            )
            .autoDispose()
    }

    fun voteInPoll(position: Int, choices: List<Int>) {
        val status = statuses[position].asRight()
        val votedPoll = status.actionableStatus.poll!!.votedCopy(choices)
        setVoteForPoll(position, status, votedPoll)
        timelineCases.voteInPoll(status, choices)
            .subscribe(
                { newPoll: Poll -> setVoteForPoll(position, status, newPoll) },
                { t: Throwable? -> Log.d(TAG, "Failed to vote in poll: " + status.id, t) }
            )
            .autoDispose()
    }

    fun changeExpanded(expanded: Boolean, position: Int) {
        val newViewData: StatusViewData = StatusViewData.Builder(
            statuses.getPairedItem(position) as StatusViewData.Concrete
        )
            .setIsExpanded(expanded).createStatusViewData()
        statuses.setPairedItem(position, newViewData)
        triggerViewUpdate()
    }

    fun changeContentHidden(isShowing: Boolean, position: Int) {
        val newViewData: StatusViewData = StatusViewData.Builder(
            statuses.getPairedItem(position) as StatusViewData.Concrete
        )
            .setIsShowingSensitiveContent(isShowing).createStatusViewData()
        statuses.setPairedItem(position, newViewData)
        triggerViewUpdate()
    }

    fun changeContentCollapsed(isCollapsed: Boolean, position: Int) {
        if (position < 0 || position >= statuses.size) {
            Log.e(
                TAG,
                String.format(
                    "Tried to access out of bounds status position: %d of %d",
                    position,
                    statuses.size - 1
                )
            )
            return
        }
        val status = statuses.getPairedItem(position)
        if (status !is StatusViewData.Concrete) {
            // Statuses PairedList contains a base type of StatusViewData.Concrete and also doesn't
            // check for null values when adding values to it although this doesn't seem to be an issue.
            Log.e(
                TAG, String.format(
                    "Expected StatusViewData.Concrete, got %s instead at position: %d of %d",
                    status?.javaClass?.simpleName ?: "<null>",
                    position,
                    statuses.size - 1
                )
            )
            return
        }
        val updatedStatus: StatusViewData = StatusViewData.Builder(status)
            .setCollapsed(isCollapsed)
            .createStatusViewData()
        statuses.setPairedItem(position, updatedStatus)
        triggerViewUpdate()
    }

    fun removeAllByAccountId(accountId: String) {
        // using iterator to safely remove items while iterating
        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next().asRightOrNull()
            if (status != null &&
                (status.account.id == accountId || status.actionableStatus.account.id == accountId)
            ) {
                iterator.remove()
            }
        }
    }

    fun removeAllByInstance(instance: String) {
        // using iterator to safely remove items while iterating
        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next().asRightOrNull()
            if (status != null && LinkHelper.getDomain(status.account.url) == instance) {
                iterator.remove()
            }
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
                        liftStatusList(response.body()!!).toMutableList(),
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
                if (!this.statuses.isEmpty()
                    && !this.statuses[this.statuses.size - 1].isRight()
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


        // TODO: ???
//        binding.topProgressBar.hide()
//        binding.swipeRefreshLayout.isEnabled = true
        updateBottomLoadingState(fetchEnd)
        this.isLoadingInitially = false
        this.isRefreshing = false
        this.triggerViewUpdate()
    }

    private fun onFetchTimelineFailure(throwable: Throwable, fetchEnd: FetchEnd, position: Int) {
        this.isRefreshing = false
        // TODO
//        binding.topProgressBar.hide()
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
        } else if (statuses.isEmpty()) {
            // TODO
//            binding.swipeRefreshLayout.isEnabled = false
//            binding.statusView.visibility = View.VISIBLE
            // TODO
//            if (throwable is IOException) {
//                binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network) {
//                    binding.progressBar.visibility = View.VISIBLE
//                    onRefresh()
//                }
//            } else {
//                binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic) {
//                    binding.progressBar.visibility = View.VISIBLE
//                    onRefresh()
//                }
//            }
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
            statuses.addAll(newStatuses)
        } else {
            val lastOfNew = newStatuses.lastOrNull()
            val index = if (lastOfNew == null) -1 else statuses.indexOf(lastOfNew)
            if (index >= 0) {
                statuses.subList(0, index).clear()
            }
            val newIndex = newStatuses.indexOf(statuses[0])
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    val placeholderId =
                        newStatuses.last { status -> status.isRight() }.asRight().id.inc()
                    newStatuses.add(Either.Left(Placeholder(placeholderId)))
                }
                statuses.addAll(0, newStatuses)
            } else {
                statuses.addAll(0, newStatuses.subList(0, newIndex))
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

    private fun filterStatuses(statuses: MutableList<Either<Placeholder, Status>>) {
//        val it = statuses.iterator()
//        while (it.hasNext()) {
////            val status = it.next().asRightOrNull()
////            if (status != null
//            // TODO
////                && (status.inReplyToId != null && filterRemoveReplies
////                        || status.reblog != null && filterRemoveReblogs
////                        || shouldFilterStatus(status.actionableStatus))
////            ) {
////                it.remove()
////            }
//        }
    }

    private fun extractNextId(response: Response<*>): String? {
        val linkHeader = response.headers()["Link"] ?: return null
        val links = HttpHeaderLink.parse(linkHeader)
        val nextHeader = HttpHeaderLink.findByRelationType(links, "next") ?: return null
        val nextLink = nextHeader.uri ?: return null
        return nextLink.getQueryParameter("max_id")
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
        triggerViewUpdate()
    }

    private fun setFavoriteForStatus(position: Int, status: Status, favourite: Boolean) {
        status.favourited = favourite
        if (status.reblog != null) {
            status.reblog.favourited = favourite
        }
        val actual = findStatusAndPosition(position, status) ?: return
        val newViewData: StatusViewData = StatusViewData.Builder(actual.first)
            .setFavourited(favourite)
            .createStatusViewData()
        statuses.setPairedItem(actual.second!!, newViewData)
        triggerViewUpdate()
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
        triggerViewUpdate()
    }

    private fun setVoteForPoll(position: Int, status: Status, newPoll: Poll) {
        val actual = findStatusAndPosition(position, status) ?: return
        val newViewData: StatusViewData = StatusViewData.Builder(actual.first)
            .setPoll(newPoll)
            .createStatusViewData()
        statuses.setPairedItem(actual.second!!, newViewData)
        triggerViewUpdate()
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
            this.statuses.addAll(statuses)

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
                val status = statuses[i]
                if (status.isRight()) {
                    firstOrNull = status.asRight().id
                    secondOrNull = statuses.getOrNull(i + 1)?.asRightOrNull()?.id
                    break
                }
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
        if (placeholder.isLeft()) {
            statuses.removeAt(pos)
        }
        if (newStatuses.isEmpty()) {
            return
        }
        if (fullFetch) {
            newStatuses.add(placeholder)
        }
        statuses.addAll(pos, newStatuses)
        removeConsecutivePlaceholders()
        triggerViewUpdate()
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
        }
    }

    private fun liftStatusList(list: List<Status>): List<Either<Placeholder, Status>> {
        return list.map(statusLifter)
    }

    /**
     * For certain requests we don't want to see placeholders, they will be removed some other way
     */
    private fun clearPlaceholdersForResponse(statuses: MutableList<Either<Placeholder, Status>>) {
        statuses.removeAll { status -> status.isLeft() }
    }

    private val statusLifter: Function1<Status, Either<Placeholder, Status>> =
        { value -> Either.Right(value) }

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
        setFavoriteForStatus(pos, status, favEvent.favourite)
    }

    private fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        val pos = findStatusOrReblogPositionById(bookmarkEvent.statusId)
        if (pos < 0) return
        val status = statuses[pos].asRight()
        setBookmarkForStatus(pos, status, bookmarkEvent.bookmark)
    }

    private fun findStatusOrReblogPositionById(statusId: String): Int {
        return statuses.indexOfFirst { either ->
            val status = either.asRightOrNull()
            status != null &&
                    (statusId == status.id ||
                            (status.reblog != null && statusId == status.reblog.id))
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
            if (either.isRight() && id == either.asRight().id) {
                statuses.remove(either)
                break
            }
        }
    }

    private fun onPreferenceChanged(key: String) {
        // TODO: do something about all this preference mess entangled with SFragment
//        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
//        when (key) {
//            PrefKeys.FAB_HIDE -> {
//                hideFab = sharedPreferences.getBoolean(PrefKeys.FAB_HIDE, false)
//            }
//            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
//                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
//                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
//                if (enabled != oldMediaPreviewEnabled) {
//                    adapter.mediaPreviewEnabled = enabled
//                    fullyRefresh()
//                }
//            }
//            PrefKeys.TAB_FILTER_HOME_REPLIES -> {
//                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
//                val oldRemoveReplies = filterRemoveReplies
//                filterRemoveReplies = kind == Kind.HOME && !filter
//                if (statuses.isNotEmpty() && oldRemoveReplies != filterRemoveReplies) {
//                    fullyRefresh()
//                }
//            }
//            PrefKeys.TAB_FILTER_HOME_BOOSTS -> {
//                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
//                val oldRemoveReblogs = filterRemoveReblogs
//                filterRemoveReblogs = kind == Kind.HOME && !filter
//                if (statuses.isNotEmpty() && oldRemoveReblogs != filterRemoveReblogs) {
//                    fullyRefresh()
//                }
//            }
//            Filter.HOME, Filter.NOTIFICATIONS, Filter.THREAD, Filter.PUBLIC, Filter.ACCOUNT -> {
//                if (filterContextMatchesKind(kind, listOf(key))) {
//                    reloadFilters(true)
//                }
//            }
//            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> {
//                //it is ok if only newly loaded statuses are affected, no need to fully refresh
//                alwaysShowSensitiveMedia =
//                    accountManager.activeAccount!!.alwaysShowSensitiveMedia
//            }
//        }
    }

    // public for now
    fun filterContextMatchesKind(
        kind: Kind?,
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

    private fun findStatusAndPosition(
        position: Int,
        status: Status
    ): Pair<StatusViewData.Concrete, Int>? {
        val statusToUpdate: StatusViewData.Concrete
        val positionToUpdate: Int
        val someOldViewData = statuses.getPairedItem(position)

        // Unlikely, but data could change between the request and response
        if (someOldViewData is StatusViewData.Placeholder ||
            (someOldViewData as StatusViewData.Concrete).id != status.id
        ) {
            // try to find the status we need to update
            val foundPos = statuses.indexOf(Either.Right(status))
            if (foundPos < 0) return null // okay, it's hopeless, give up
            statusToUpdate = statuses.getPairedItem(foundPos) as StatusViewData.Concrete
            positionToUpdate = position
        } else {
            statusToUpdate = someOldViewData
            positionToUpdate = position
        }
        return Pair(statusToUpdate, positionToUpdate)
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