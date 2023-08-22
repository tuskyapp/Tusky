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
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.timeline.CachedTimelineRepository
import com.keylesspalace.tusky.components.timeline.FiltersRepository
import com.keylesspalace.tusky.components.timeline.TimelineKind
import com.keylesspalace.tusky.components.timeline.toViewData
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.settings.AccountPreferenceDataStore
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TimelineViewModel that caches all statuses in a local database
 */
class CachedTimelineViewModel @Inject constructor(
    private val repository: CachedTimelineRepository,
    timelineCases: TimelineCases,
    eventHub: EventHub,
    filtersRepository: FiltersRepository,
    accountManager: AccountManager,
    preferences: SharedPreferences,
    accountPreferenceDataStore: AccountPreferenceDataStore,
    filterModel: FilterModel,
    private val gson: Gson
) : TimelineViewModel(
    timelineCases,
    eventHub,
    filtersRepository,
    accountManager,
    preferences,
    accountPreferenceDataStore,
    filterModel
) {

    override lateinit var statuses: Flow<PagingData<StatusViewData>>

    init {
        readingPositionId = activeAccount.lastVisibleHomeTimelineStatusId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init(timelineKind: TimelineKind) {
        super.init(timelineKind)
        statuses = merge(getUiPrefs(), reload)
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
        return repository.getStatusStream(kind = kind, initialKey = initialKey)
            .map { pagingData ->
                pagingData
                    .map { it.toViewData(gson) }
                    .filter { shouldFilterStatus(it) != Filter.Action.HIDE }
            }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData) {
        // handled by CacheUpdater
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData) {
        viewModelScope.launch {
            repository.setExpanded(expanded, status.id)
        }
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData) {
        viewModelScope.launch {
            repository.setContentShowing(isShowing, status.id)
        }
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData) {
        viewModelScope.launch {
            repository.setContentCollapsed(isCollapsed, status.id)
        }
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

    override fun clearWarning(status: StatusViewData) {
        viewModelScope.launch {
            repository.clearStatusWarning(status.actionableId)
        }
    }

    override fun removeStatusWithId(id: String) {
        // handled by CacheUpdater
    }

    override fun handleReblogEvent(reblogEvent: ReblogEvent) {
        // handled by CacheUpdater
    }

    override fun handleFavEvent(favEvent: FavoriteEvent) {
        // handled by CacheUpdater
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        // handled by CacheUpdater
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        // handled by CacheUpdater
    }

    override fun reloadKeepingReadingPosition() {
        super.reloadKeepingReadingPosition()
        viewModelScope.launch {
            repository.clearAndReload()
        }
    }

    override fun reloadFromNewest() {
        super.reloadFromNewest()
        viewModelScope.launch {
            repository.clearAndReloadFromNewest()
        }
    }

    override suspend fun invalidate() {
        repository.invalidate()
    }

    companion object {
        private const val TAG = "CachedTimelineViewModel"
    }
}
