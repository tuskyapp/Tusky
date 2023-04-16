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
import com.keylesspalace.tusky.components.timeline.toViewData
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * TimelineViewModel that caches all statuses in a local database
 */
class CachedTimelineViewModel @Inject constructor(
    private val repository: CachedTimelineRepository,
    timelineCases: TimelineCases,
    api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    preferences: SharedPreferences,
    filterModel: FilterModel,
    private val db: AppDatabase,
    private val gson: Gson
) : TimelineViewModel(
    timelineCases,
    api,
    eventHub,
    accountManager,
    preferences,
    filterModel
) {

    override lateinit var statuses: Flow<PagingData<StatusViewData>>

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init(timelineKind: TimelineKind) {
        super.init(timelineKind)
        statuses = getUiPrefs()
            .flatMapLatest {
                getStatuses(timelineKind)
            }.cachedIn(viewModelScope)
    }

    /** @return Flow of statuses that make up the timeline of [kind] */
    private fun getStatuses(
        kind: TimelineKind,
        initialKey: String? = null
    ): Flow<PagingData<StatusViewData>> {
        return repository.getStatusStream(kind = kind, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map {
                    it.toViewData(gson)
                }
            }.map {
                // TODO: These operations happen in a sub-optimal order. Ideally we could do
                // any filtering of the statuses before the cost of converting them to viewdata.
                // However, TimelineStatusWithAccount does not provide access to the `Status`
                // type that is needed to do the filtering, so it has to be converted to a
                // `StatusViewData` first.
                it.filter {
                    shouldFilterStatus(it.status) != Filter.Action.HIDE
                }
            }

        // TODO:
        // - Does the above need a .flowOn(Dispatches.Default)
        // - Ditto for the same code in NetworkTimelineViewModel (check NotificationsViewModel)
    }

    init {
        // TODO: This probably shouldn't be done here, but be a WorkManager job
        viewModelScope.launch {
            delay(5.toDuration(DurationUnit.SECONDS)) // delay so the db is not locked during initial ui refresh
            accountManager.activeAccount?.id?.let { accountId ->
                db.timelineDao().cleanup(accountId, MAX_STATUSES_IN_CACHE)
                db.timelineDao().cleanupAccounts(accountId)
            }
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

    override fun fullReload() {
        // TODO: Don't touch the db directly, go through the repository
//        viewModelScope.launch {
//            val activeAccount = accountManager.activeAccount!!
//            db.timelineDao().removeAll(activeAccount.id)
//        }
        viewModelScope.launch {
            invalidate()
        }
    }

    override suspend fun invalidate() {
        repository.invalidate()
    }

    companion object {
        private const val MAX_STATUSES_IN_CACHE = 1000
    }
}
