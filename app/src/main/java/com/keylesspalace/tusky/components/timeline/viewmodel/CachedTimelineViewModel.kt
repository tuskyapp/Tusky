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
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.map
import at.connyduck.calladapter.networkresult.onFailure
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder.NEWEST_FIRST
import com.keylesspalace.tusky.components.preference.PreferencesFragment.ReadingOrder.OLDEST_FIRST
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.components.timeline.toStatus
import com.keylesspalace.tusky.components.timeline.toViewData
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.entity.HomeTimelineData
import com.keylesspalace.tusky.db.entity.HomeTimelineEntity
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * TimelineViewModel that caches all statuses in a local database
 */
@HiltViewModel
class CachedTimelineViewModel @Inject constructor(
    timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel,
    private val db: AppDatabase
) : TimelineViewModel(
    timelineCases,
    eventHub,
    accountManager,
    sharedPreferences,
    filterModel
) {

    private var currentPagingSource: PagingSource<Int, HomeTimelineData>? = null

    /** Map from status id to translation. */
    private val translations = MutableStateFlow(mapOf<String, TranslationViewData>())

    @OptIn(ExperimentalPagingApi::class)
    override val statuses = Pager(
        config = PagingConfig(
            pageSize = LOAD_AT_ONCE
        ),
        remoteMediator = CachedTimelineRemoteMediator(this, api, db),
        pagingSourceFactory = {
            db.timelineDao().getHomeTimeline(accountId).also { newPagingSource ->
                this.currentPagingSource = newPagingSource
            }
        }
    ).flow
        // Apply cachedIn() early to be able to combine with translation flow.
        // This will not cache ViewData's but practically we don't need this.
        // If you notice that this flow is used in more than once place consider
        // adding another cachedIn() for the overall result.
        .cachedIn(viewModelScope)
        .combine(translations) { pagingData, translations ->
            pagingData.map { timelineData ->
                val translation = translations[timelineData.status?.serverId]
                val status = timelineData.account?.let { timelineData.status?.toStatus(it) }
                val filter = status?.let { shouldFilterStatus(it) }
                timelineData.toViewData(
                    isDetailed = false,
                    translation = translation,
                    filter = filter,
                )
            }.filter { statusViewData ->
                statusViewData.filter?.action != Filter.Action.HIDE
            }
        }
        .flowOn(Dispatchers.Default)

    override fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao()
                .setExpanded(accountId, status.actionableId, expanded)
        }
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao()
                .setContentShowing(accountId, status.actionableId, isShowing)
        }
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao()
                .setContentCollapsed(accountId, status.actionableId, isCollapsed)
        }
    }

    override fun clearWarning(status: StatusViewData.Concrete) {
        viewModelScope.launch {
            db.timelineStatusDao().clearWarning(accountId, status.actionableId)
        }
    }

    override fun removeStatusWithId(id: String) {
        // handled by CacheUpdater
    }

    override fun loadMore(placeholderId: String) {
        viewModelScope.launch {
            try {
                val timelineDao = db.timelineDao()
                val statusDao = db.timelineStatusDao()
                val accountDao = db.timelineAccountDao()

                timelineDao.insertHomeTimelineItem(
                    Placeholder(placeholderId, loading = true).toEntity(tuskyAccountId = accountId)
                )

                val (idAbovePlaceholder, idBelowPlaceholder) = db.withTransaction {
                    timelineDao.getIdAbove(accountId, placeholderId) to
                        timelineDao.getIdBelow(accountId, placeholderId)
                }
                val response = when (readingOrder) {
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

                val statuses = response.body()
                if (!response.isSuccessful || statuses == null) {
                    loadMoreFailed(placeholderId, HttpException(response))
                    return@launch
                }

                val account = activeAccountFlow.value
                if (account == null) {
                    return@launch
                }

                db.withTransaction {
                    timelineDao.deleteHomeTimelineItem(accountId, placeholderId)

                    val overlappedStatuses = if (statuses.isNotEmpty()) {
                        timelineDao.deleteRange(
                            accountId,
                            statuses.last().id,
                            statuses.first().id
                        )
                    } else {
                        0
                    }

                    for (status in statuses) {
                        accountDao.insert(status.account.toEntity(accountId))
                        status.reblog?.account?.toEntity(accountId)
                            ?.let { rebloggedAccount ->
                                accountDao.insert(rebloggedAccount)
                            }
                        val filter = status.getApplicableFilter(kind.toFilterKind())
                        statusDao.insert(
                            status.actionableStatus.toEntity(
                                tuskyAccountId = accountId,
                                expanded = account.alwaysOpenSpoiler,
                                contentShowing = account.alwaysShowSensitiveMedia || (!status.actionableStatus.sensitive && filter?.action != Filter.Action.BLUR),
                                contentCollapsed = true
                            )
                        )
                        timelineDao.insertHomeTimelineItem(
                            HomeTimelineEntity(
                                tuskyAccountId = accountId,
                                id = status.id,
                                statusId = status.actionableId,
                                reblogAccountId = if (status.reblog != null) {
                                    status.account.id
                                } else {
                                    null
                                }
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
                        timelineDao.insertHomeTimelineItem(
                            Placeholder(
                                idToConvert,
                                loading = false
                            ).toEntity(accountId)
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
        Log.w(TAG, "failed loading statuses", e)
        val activeAccount = accountManager.activeAccount!!
        db.timelineDao()
            .insertHomeTimelineItem(Placeholder(placeholderId, loading = false).toEntity(activeAccount.id))
    }

    override fun fullReload() {
        viewModelScope.launch {
            val activeAccount = accountManager.activeAccount!!
            db.timelineDao().removeAllHomeTimelineItems(activeAccount.id)
        }
    }

    override fun saveReadingPosition(statusId: String) {
        viewModelScope.launch {
            accountManager.activeAccount?.let { account ->
                Log.d(TAG, "Saving position at: $statusId")
                accountManager.updateAccount(account) {
                    copy(lastVisibleHomeTimelineStatusId = statusId)
                }
            }
        }
    }

    override suspend fun invalidate() {
        // invalidating when we don't have statuses yet can cause empty timelines because it cancels the network load
        if (db.timelineDao().getHomeTimelineItemCount(accountId) > 0) {
            currentPagingSource?.invalidate()
        }
    }

    override suspend fun translate(status: StatusViewData.Concrete): NetworkResult<Unit> {
        translations.value += (status.id to TranslationViewData.Loading)
        return timelineCases.translate(status.actionableId)
            .map { translation ->
                translations.value += (status.actionableId to TranslationViewData.Loaded(translation))
            }
            .onFailure {
                translations.value -= status.actionableId
            }
    }

    override fun untranslate(status: StatusViewData.Concrete) {
        translations.value -= status.actionableId
    }

    companion object {
        private const val TAG = "CachedTimelineViewModel"
    }
}
