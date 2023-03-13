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

package com.keylesspalace.tusky.components.timeline.viewmodel

import android.content.SharedPreferences
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.timeline.NetworkTimelineRepository
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.getDomain
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * TimelineViewModel that caches all statuses in an in-memory list
 */
class NetworkTimelineViewModel @Inject constructor(
    private val repository: NetworkTimelineRepository,
    timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel
) : TimelineViewModel(timelineCases, api, eventHub, accountManager, sharedPreferences, filterModel) {

    var currentSource: NetworkTimelinePagingSource? = null

    val statusData: MutableList<Status> = mutableListOf()

    var nextKey: String? = null

    // TODO: This is janky because timelineKind isn't valid until init() is run, and is needed
    // to know what timeline to get. Hence the lateinit in here and the need to override init()
    // afterwards.

    override lateinit var statuses: Flow<PagingData<StatusViewData>>

    override fun init(timelineKind: TimelineKind) {
        super.init(timelineKind)
        statuses = getStatuses(timelineKind)
    }

    /** @return FLow of statuses that make up the timeline of [kind] */
    private fun getStatuses(
        kind: TimelineKind,
        initialKey: String? = null
    ): Flow<PagingData<StatusViewData>> {
        return repository.getStatusStream(kind = kind, initialKey = initialKey)
            .map { pagingData ->
                pagingData.filter {
                    shouldFilterStatus(it) != Filter.Action.HIDE
                }.map {
                    // TODO: The previous code in RemoteMediator checked the states against the
                    // previous version of the status to make sure they were replicated. This will
                    // need to be reimplemented (probably as a map of StatusId -> ViewStates.
                    // For now, just use the user's preferences.
                    it.toViewData(
                        isShowingContent = alwaysShowSensitiveMedia || !it.actionableStatus.sensitive,
                        isExpanded = alwaysOpenSpoilers,
                        isCollapsed = true
                    )
                }
            }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData.Concrete) {
        status.copy(
            status = status.status.copy(poll = newPoll)
        ).update()
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        status.copy(
            isExpanded = expanded
        ).update()
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        status.copy(
            isShowingContent = isShowing
        ).update()
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        status.copy(
            isCollapsed = isCollapsed
        ).update()
    }

    override fun removeAllByAccountId(accountId: String) {
        statusData.removeAll { status ->
            status.account.id == accountId || status.actionableStatus.account.id == accountId
        }
        currentSource?.invalidate()
    }

    override fun removeAllByInstance(instance: String) {
        statusData.removeAll { status ->
            getDomain(status.account.url) == instance
        }
        currentSource?.invalidate()
    }

    override fun removeStatusWithId(id: String) {
        statusData.removeAll { status ->
            status.id == id || status.reblog?.id == id
        }
        currentSource?.invalidate()
    }

    override fun loadMore(placeholderId: String) {
//        viewModelScope.launch {
//            try {
//                val placeholderIndex =
//                    statusData.indexOfFirst { it is StatusViewData.Placeholder && it.id == placeholderId }
//                statusData[placeholderIndex] = StatusViewData.Placeholder(placeholderId, isLoading = true)
//
//                val idAbovePlaceholder = statusData.getOrNull(placeholderIndex - 1)?.id
//
//                val statusResponse = fetchStatusesForKind(
//                    fromId = idAbovePlaceholder,
//                    uptoId = null,
//                    limit = 20
//                )
//
//                val statuses = statusResponse.body()
//                if (!statusResponse.isSuccessful || statuses == null) {
//                    loadMoreFailed(placeholderId, HttpException(statusResponse))
//                    return@launch
//                }
//
//                statusData.removeAt(placeholderIndex)
//
//                val activeAccount = accountManager.activeAccount!!
//                val data: MutableList<StatusViewData> = statuses.map { status ->
//                    status.toViewData(
//                        isShowingContent = activeAccount.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
//                        isExpanded = activeAccount.alwaysOpenSpoiler,
//                        isCollapsed = true
//                    )
//                }.toMutableList()
//
//                if (statuses.isNotEmpty()) {
//                    val firstId = statuses.first().id
//                    val lastId = statuses.last().id
//                    val overlappedFrom = statusData.indexOfFirst { it.id.isLessThanOrEqual(firstId) ?: false }
//                    val overlappedTo = statusData.indexOfFirst { it.id.isLessThan(lastId) ?: false }
//
//                    if (overlappedFrom < overlappedTo) {
//                        data.mapIndexed { i, status -> i to statusData.firstOrNull { it.id == status.id }?.asStatusOrNull() }
//                            .filter { (_, oldStatus) -> oldStatus != null }
//                            .forEach { (i, oldStatus) ->
//                                data[i] = data[i].asStatusOrNull()!!
//                                    .copy(
//                                        isShowingContent = oldStatus!!.isShowingContent,
//                                        isExpanded = oldStatus.isExpanded,
//                                        isCollapsed = oldStatus.isCollapsed,
//                                    )
//                            }
//
//                        statusData.removeAll { status ->
//                            lastId.isLessThan(status.id) && status.id.isLessThanOrEqual(firstId)
//                        }
//                    } else {
//                        data[data.size - 1] = StatusViewData.Placeholder(statuses.last().id, isLoading = false)
//                    }
//                }
//
//                statusData.addAll(placeholderIndex, data)
//
//                currentSource?.invalidate()
//            } catch (e: Exception) {
//                ifExpected(e) {
//                    loadMoreFailed(placeholderId, e)
//                }
//            }
//        }
    }

//    private fun loadMoreFailed(placeholderId: String, e: Exception) {
//        Log.w("NetworkTimelineVM", "failed loading statuses", e)
//
//        val index =
//            statusData.indexOfFirst { it is StatusViewData.Placeholder && it.id == placeholderId }
//        statusData[index] = StatusViewData.Placeholder(placeholderId, isLoading = false)
//
//        currentSource?.invalidate()
//    }

    override fun handleReblogEvent(reblogEvent: ReblogEvent) {
        updateStatusById(reblogEvent.statusId) {
            it.copy(status = it.status.copy(reblogged = reblogEvent.reblog))
        }
    }

    override fun handleFavEvent(favEvent: FavoriteEvent) {
        updateActionableStatusById(favEvent.statusId) {
            it.copy(favourited = favEvent.favourite)
        }
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        updateActionableStatusById(bookmarkEvent.statusId) {
            it.copy(bookmarked = bookmarkEvent.bookmark)
        }
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        updateActionableStatusById(pinEvent.statusId) {
            it.copy(pinned = pinEvent.pinned)
        }
    }

    override fun fullReload() {
        nextKey = statusData.firstOrNull()?.id
        statusData.clear()
        currentSource?.invalidate()
    }

    override fun clearWarning(status: StatusViewData.Concrete) {
        updateActionableStatusById(status.actionableId) {
            it.copy(filtered = null)
        }
    }

    override suspend fun invalidate() {
        currentSource?.invalidate()
    }

    private fun StatusViewData.Concrete.update() {
//        val position = statusData.indexOfFirst { viewData -> viewData.asStatusOrNull()?.id == this.id }
//        statusData[position] = this
//        currentSource?.invalidate()
    }

    private inline fun updateStatusById(
        id: String,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val pos = statusData.indexOfFirst { it.id == id }
        if (pos == -1) return
//        updateViewDataAt(pos, updater)
    }

    private inline fun updateActionableStatusById(
        id: String,
        updater: (Status) -> Status
    ) {
        val pos = statusData.indexOfFirst { it.id == id }
        if (pos == -1) return
//        updateViewDataAt(pos) { vd ->
//            if (vd.status.reblog != null) {
//                vd.copy(status = vd.status.copy(reblog = updater(vd.status.reblog)))
//            } else {
//                vd.copy(status = updater(vd.status))
//            }
//        }
    }

//    private inline fun updateViewDataAt(
//        position: Int,
//        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
//    ) {
//        val status = statusData.getOrNull(position)?.asStatusOrNull() ?: return
//        statusData[position] = updater(status)
//        currentSource?.invalidate()
//    }
}
