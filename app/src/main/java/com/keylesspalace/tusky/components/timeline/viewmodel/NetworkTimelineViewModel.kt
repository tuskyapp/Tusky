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
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.settings.AccountPreferenceDataStore
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    accountPreferenceDataStore: AccountPreferenceDataStore,
    filterModel: FilterModel
) : TimelineViewModel(
    timelineCases,
    eventHub,
    filtersRepository,
    accountManager,
    sharedPreferences,
    accountPreferenceDataStore,
    filterModel
) {
    private val modifiedViewData = mutableMapOf<String, StatusViewData>()

    override lateinit var statuses: Flow<PagingData<StatusViewData>>

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init(timelineKind: TimelineKind) {
        super.init(timelineKind)
        statuses = reload
            .flatMapLatest {
                getStatuses(timelineKind, initialKey = getInitialKey())
            }.cachedIn(viewModelScope)
    }

    /** @return Flow of statuses that make up the timeline of [kind] */
    private fun getStatuses(
        kind: TimelineKind,
        initialKey: String? = null
    ): Flow<PagingData<StatusViewData>> {
        Log.d(TAG, "getStatuses: kind: $kind, initialKey: $initialKey")
        return repository.getStatusStream(viewModelScope, kind = kind, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map {
                    modifiedViewData[it.id] ?: it.toViewData(
                        isShowingContent = statusDisplayOptions.value.showSensitiveMedia || !it.actionableStatus.sensitive,
                        isExpanded = statusDisplayOptions.value.openSpoiler,
                        isCollapsed = true
                    )
                }.filter {
                    shouldFilterStatus(it) != Filter.Action.HIDE
                }
            }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData) {
        modifiedViewData[status.id] = status.copy(
            status = status.status.copy(poll = newPoll)
        )
        repository.invalidate()
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData) {
        modifiedViewData[status.id] = status.copy(
            isExpanded = expanded
        )
        repository.invalidate()
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData) {
        modifiedViewData[status.id] = status.copy(
            isShowingContent = isShowing
        )
        repository.invalidate()
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData) {
        Log.d(TAG, "changeContentCollapsed: $isCollapsed")
        Log.d(TAG, "  " + status.content)
        modifiedViewData[status.id] = status.copy(
            isCollapsed = isCollapsed
        )
        repository.invalidate()
    }

    override fun removeAllByAccountId(accountId: String) {
        viewModelScope.launch {
            repository.removeAllByAccountId(accountId)
        }
    }

    override fun removeAllByInstance(instance: String) {
        viewModelScope.launch {
            repository.removeAllByInstance(instance)
        }
    }

    override fun removeStatusWithId(id: String) {
        viewModelScope.launch {
            repository.removeStatusWithId(id)
        }
    }

    override fun handleReblogEvent(reblogEvent: ReblogEvent) {
        viewModelScope.launch {
            repository.updateStatusById(reblogEvent.statusId) {
                it.copy(reblogged = reblogEvent.reblog)
            }
        }
    }

    override fun handleFavEvent(favEvent: FavoriteEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(favEvent.statusId) {
                it.copy(favourited = favEvent.favourite)
            }
        }
        repository.invalidate()
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(bookmarkEvent.statusId) {
                it.copy(bookmarked = bookmarkEvent.bookmark)
            }
        }
        repository.invalidate()
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(pinEvent.statusId) {
                it.copy(pinned = pinEvent.pinned)
            }
        }
        repository.invalidate()
    }

    override fun reloadKeepingReadingPosition() {
        super.reloadKeepingReadingPosition()
        viewModelScope.launch {
            repository.reload()
        }
    }

    override fun reloadFromNewest() {
        super.reloadFromNewest()
        reloadKeepingReadingPosition()
    }

    override fun clearWarning(status: StatusViewData) {
        viewModelScope.launch {
            repository.updateActionableStatusById(status.actionableId) {
                it.copy(filtered = null)
            }
        }
    }

    override suspend fun invalidate() {
        repository.invalidate()
    }

    companion object {
        private const val TAG = "NetworkTimelineViewModel"
    }
}
