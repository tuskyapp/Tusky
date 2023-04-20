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
import android.util.Log
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.timeline.FiltersRepository
import com.keylesspalace.tusky.components.timeline.NetworkTimelineRepository
import com.keylesspalace.tusky.components.timeline.TimelineKind
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
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
    eventHub: EventHub,
    filtersRepository: FiltersRepository,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel
) : TimelineViewModel(
    timelineCases,
    eventHub,
    filtersRepository,
    accountManager,
    sharedPreferences,
    filterModel
) {

    private var currentSource: NetworkTimelinePagingSource? = null

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

    /** @return Flow of statuses that make up the timeline of [kind] */
    private fun getStatuses(
        kind: TimelineKind,
        initialKey: String? = null
    ): Flow<PagingData<StatusViewData>> {
        Log.d(TAG, "getStatuses: kind: $kind, initialKey: $initialKey")
        return repository.getStatusStream(kind = kind, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map {
                    // TODO: The previous code in RemoteMediator checked the states against the
                    // previous version of the status to make sure they were replicated. This will
                    // need to be reimplemented (probably as a map of StatusId -> ViewStates.
                    // For now, just use the user's preferences.
                    it.toViewData(
                        isShowingContent = alwaysShowSensitiveMedia || !it.actionableStatus.sensitive,
                        isExpanded = alwaysOpenSpoilers,
                        isCollapsed = true
                    )
                }.filter {
                    shouldFilterStatus(it) != Filter.Action.HIDE
                }
            }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData) {
        status.copy(
            status = status.status.copy(poll = newPoll)
        ).update()
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData) {
        status.copy(
            isExpanded = expanded
        ).update()
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData) {
        status.copy(
            isShowingContent = isShowing
        ).update()
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData) {
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

    override fun clearWarning(status: StatusViewData) {
        updateActionableStatusById(status.actionableId) {
            it.copy(filtered = null)
        }
    }

    override suspend fun invalidate() {
        currentSource?.invalidate()
    }

    private fun StatusViewData.update() {
//        val position = statusData.indexOfFirst { viewData -> viewData.asStatusOrNull()?.id == this.id }
//        statusData[position] = this
//        currentSource?.invalidate()
    }

    private inline fun updateStatusById(
        id: String,
        updater: (StatusViewData) -> StatusViewData
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
//        updater: (StatusViewData) -> StatusViewData
//    ) {
//        val status = statusData.getOrNull(position)?.asStatusOrNull() ?: return
//        statusData[position] = updater(status)
//        currentSource?.invalidate()
//    }

    companion object {
        private const val TAG = "NetworkTimelineViewModel"
    }
}
