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
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import androidx.room.withTransaction
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder.NEWEST_FIRST
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder.OLDEST_FIRST
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.components.timeline.toViewData
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.EmptyPagingSource
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * TimelineViewModel that caches all statuses in a local database
 */
class CachedTimelineViewModel @Inject constructor(
    timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel,
    private val db: AppDatabase,
    private val gson: Gson
) : TimelineViewModel(
    timelineCases,
    api,
    eventHub,
    accountManager,
    sharedPreferences,
    filterModel
) {

    private var currentPagingSource: PagingSource<Int, TimelineStatusWithAccount>? = null

    @OptIn(ExperimentalPagingApi::class)
    override val statuses = Pager(
        config = PagingConfig(pageSize = LOAD_AT_ONCE),
        remoteMediator = CachedTimelineRemoteMediator(accountManager, api, db, gson),
        pagingSourceFactory = {
            val activeAccount = accountManager.activeAccount
            if (activeAccount == null) {
                EmptyPagingSource()
            } else {
                db.timelineDao().getStatuses(activeAccount.id)
            }.also { newPagingSource ->
                this.currentPagingSource = newPagingSource
            }
        }
    ).flow
        .map { pagingData ->
            pagingData.map(Dispatchers.Default.asExecutor()) { timelineStatus ->
                timelineStatus.toViewData(gson)
            }.filter(Dispatchers.Default.asExecutor()) { statusViewData ->
                !shouldFilterStatus(statusViewData)
            }
        }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            delay(5.toDuration(DurationUnit.SECONDS)) // delay so the db is not locked during initial ui refresh
            accountManager.activeAccount?.id?.let { accountId ->
                db.timelineDao().cleanup(accountId, MAX_STATUSES_IN_CACHE)
                db.timelineDao().cleanupAccounts(accountId)
            }
        }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData.Concrete) {
        // handled by CacheUpdater
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao().setExpanded(accountManager.activeAccount!!.id, status.id, expanded)
        }
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao()
                .setContentShowing(accountManager.activeAccount!!.id, status.id, isShowing)
        }
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineDao()
                .setContentCollapsed(accountManager.activeAccount!!.id, status.id, isCollapsed)
        }
    }

    override fun removeAllByAccountId(accountId: String) {
        viewModelScope.launch {
            db.timelineDao().removeAllByUser(accountManager.activeAccount!!.id, accountId)
        }
    }

    override fun removeAllByInstance(instance: String) {
        viewModelScope.launch {
            db.timelineDao().deleteAllFromInstance(accountManager.activeAccount!!.id, instance)
        }
    }

    override fun removeStatusWithId(id: String) {
        // handled by CacheUpdater
    }

    override fun loadMore(placeholderId: String) {
        viewModelScope.launch {
            try {
                val timelineDao = db.timelineDao()

                val activeAccount = accountManager.activeAccount!!

                timelineDao.insertStatus(
                    Placeholder(placeholderId, loading = true).toEntity(
                        activeAccount.id
                    )
                )

                val response = db.withTransaction {
                    val idAbovePlaceholder = timelineDao.getIdAbove(activeAccount.id, placeholderId)
                    val idBelowPlaceholder = timelineDao.getIdBelow(activeAccount.id, placeholderId)
                    when (readingOrder) {
                        // Using minId, loads up to LOAD_AT_ONCE statuses with IDs immediately
                        // after minId and no larger than maxId
                        OLDEST_FIRST -> api.homeTimeline(
                            maxId = idAbovePlaceholder,
                            minId = idBelowPlaceholder,
                            limit = LOAD_AT_ONCE
                        )
                        // Using sinceId, loads up to LOAD_AT_ONCE statuses immediately before
                        // maxId, and no smaller than minId.
                        NEWEST_FIRST -> api.homeTimeline(
                            maxId = idAbovePlaceholder,
                            sinceId = idBelowPlaceholder,
                            limit = LOAD_AT_ONCE
                        )
                    }
                }

                val statuses = response.body()
                if (!response.isSuccessful || statuses == null) {
                    loadMoreFailed(placeholderId, HttpException(response))
                    return@launch
                }

                db.withTransaction {

                    timelineDao.delete(activeAccount.id, placeholderId)

                    val overlappedStatuses = if (statuses.isNotEmpty()) {
                        timelineDao.deleteRange(
                            activeAccount.id,
                            statuses.last().id,
                            statuses.first().id
                        )
                    } else {
                        0
                    }

                    for (status in statuses) {
                        timelineDao.insertAccount(status.account.toEntity(activeAccount.id, gson))
                        status.reblog?.account?.toEntity(activeAccount.id, gson)
                            ?.let { rebloggedAccount ->
                                timelineDao.insertAccount(rebloggedAccount)
                            }
                        timelineDao.insertStatus(
                            status.toEntity(
                                timelineUserId = activeAccount.id,
                                gson = gson,
                                expanded = activeAccount.alwaysOpenSpoiler,
                                contentShowing = activeAccount.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
                                contentCollapsed = true
                            )
                        )
                    }

                    /* In case we loaded a whole page and there was no overlap with existing statuses,
                       we insert a placeholder because there might be even more unknown statuses */
                    if (overlappedStatuses == 0 && statuses.size == LOAD_AT_ONCE) {
                        /* This overrides the first/last of the newly loaded statuses with a placeholder
                           to guarantee the placeholder has an id that exists on the server as not all
                           servers handle client generated ids as expected */
                        val idToConvert = when (readingOrder) {
                            OLDEST_FIRST -> statuses.first().id
                            NEWEST_FIRST -> statuses.last().id
                        }
                        timelineDao.insertStatus(
                            Placeholder(
                                idToConvert,
                                loading = false
                            ).toEntity(activeAccount.id)
                        )
                    }
                }
            } catch (e: Exception) {
                ifExpected(e) {
                    loadMoreFailed(placeholderId, e)
                }
            }
        }
    }

    private suspend fun loadMoreFailed(placeholderId: String, e: Exception) {
        Log.w("CachedTimelineVM", "failed loading statuses", e)
        val activeAccount = accountManager.activeAccount!!
        db.timelineDao()
            .insertStatus(Placeholder(placeholderId, loading = false).toEntity(activeAccount.id))
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
        viewModelScope.launch {
            val activeAccount = accountManager.activeAccount!!
            db.timelineDao().removeAll(activeAccount.id)
        }
    }

    override suspend fun invalidate() {
        // invalidating when we don't have statuses yet can cause empty timelines because it cancels the network load
        if (db.timelineDao().getStatusCount(accountManager.activeAccount!!.id) > 0) {
            currentPagingSource?.invalidate()
        }
    }

    companion object {
        private const val MAX_STATUSES_IN_CACHE = 1000
    }
}
