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
import androidx.paging.cachedIn
import androidx.paging.filter
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.map
import at.connyduck.calladapter.networkresult.onFailure
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.DomainMuteEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.getDomain
import com.keylesspalace.tusky.util.isLessThan
import com.keylesspalace.tusky.util.isLessThanOrEqual
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response

/**
 * TimelineViewModel that caches all statuses in an in-memory list
 */
@HiltViewModel
class NetworkTimelineViewModel @Inject constructor(
    timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel
) : TimelineViewModel(
    timelineCases,
    api,
    eventHub,
    accountManager,
    sharedPreferences,
    filterModel
) {

    var currentSource: NetworkTimelinePagingSource? = null

    val statusData: MutableList<StatusViewData> = mutableListOf()

    var nextKey: String? = null

    @OptIn(ExperimentalPagingApi::class)
    override val statuses = Pager(
        config = PagingConfig(pageSize = LOAD_AT_ONCE),
        pagingSourceFactory = {
            NetworkTimelinePagingSource(
                viewModel = this
            ).also { source ->
                currentSource = source
            }
        },
        remoteMediator = NetworkTimelineRemoteMediator(accountManager, this)
    ).flow
        .map { pagingData ->
            pagingData.filter(Dispatchers.Default.asExecutor()) { statusViewData ->
                shouldFilterStatus(statusViewData) != Filter.Action.HIDE
            }
        }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            eventHub.events
                .collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is StatusChangedEvent -> handleStatusChangedEvent(event.status)
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
                    removeStatusWithId(event.statusId)
                }
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

    private fun removeAllByAccountId(accountId: String) {
        statusData.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            status.account.id == accountId || status.actionableStatus.account.id == accountId
        }
        currentSource?.invalidate()
    }

    private fun removeAllByInstance(instance: String) {
        statusData.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            getDomain(status.account.url) == instance
        }
        currentSource?.invalidate()
    }

    override fun removeStatusWithId(id: String) {
        statusData.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            status.id == id || status.reblog?.id == id
        }
        currentSource?.invalidate()
    }

    override fun loadMore(placeholderId: String) {
        viewModelScope.launch {
            try {
                val placeholderIndex =
                    statusData.indexOfFirst { it is StatusViewData.Placeholder && it.id == placeholderId }
                statusData[placeholderIndex] =
                    StatusViewData.Placeholder(placeholderId, isLoading = true)

                val idAbovePlaceholder = statusData.getOrNull(placeholderIndex - 1)?.id

                val statusResponse = fetchStatusesForKind(
                    fromId = idAbovePlaceholder,
                    uptoId = null,
                    limit = 20
                )

                val statuses = statusResponse.body()
                if (!statusResponse.isSuccessful || statuses == null) {
                    loadMoreFailed(placeholderId, HttpException(statusResponse))
                    return@launch
                }

                statusData.removeAt(placeholderIndex)

                val activeAccount = accountManager.activeAccount!!
                val data: MutableList<StatusViewData> = statuses.map { status ->
                    status.toViewData(
                        isShowingContent = activeAccount.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
                        isExpanded = activeAccount.alwaysOpenSpoiler,
                        isCollapsed = true
                    )
                }.toMutableList()

                if (statuses.isNotEmpty()) {
                    val firstId = statuses.first().id
                    val lastId = statuses.last().id
                    val overlappedFrom = statusData.indexOfFirst {
                        it.asStatusOrNull()?.id?.isLessThanOrEqual(firstId) ?: false
                    }
                    val overlappedTo = statusData.indexOfFirst {
                        it.asStatusOrNull()?.id?.isLessThan(lastId) ?: false
                    }

                    if (overlappedFrom < overlappedTo) {
                        data.mapIndexed { i, status ->
                            i to statusData.firstOrNull {
                                it.asStatusOrNull()?.id == status.id
                            }?.asStatusOrNull()
                        }
                            .filter { (_, oldStatus) -> oldStatus != null }
                            .forEach { (i, oldStatus) ->
                                data[i] = data[i].asStatusOrNull()!!
                                    .copy(
                                        isShowingContent = oldStatus!!.isShowingContent,
                                        isExpanded = oldStatus.isExpanded,
                                        isCollapsed = oldStatus.isCollapsed
                                    )
                            }

                        statusData.removeAll { status ->
                            when (status) {
                                is StatusViewData.Placeholder -> lastId.isLessThan(status.id) && status.id.isLessThanOrEqual(
                                    firstId
                                )

                                is StatusViewData.Concrete -> lastId.isLessThan(status.id) && status.id.isLessThanOrEqual(
                                    firstId
                                )
                            }
                        }
                    } else {
                        data[data.size - 1] =
                            StatusViewData.Placeholder(statuses.last().id, isLoading = false)
                    }
                }

                statusData.addAll(placeholderIndex, data)

                currentSource?.invalidate()
            } catch (e: Exception) {
                ifExpected(e) {
                    loadMoreFailed(placeholderId, e)
                }
            }
        }
    }

    private fun loadMoreFailed(placeholderId: String, e: Exception) {
        Log.w("NetworkTimelineVM", "failed loading statuses", e)

        val index =
            statusData.indexOfFirst { it is StatusViewData.Placeholder && it.id == placeholderId }
        statusData[index] = StatusViewData.Placeholder(placeholderId, isLoading = false)

        currentSource?.invalidate()
    }

    private fun handleStatusChangedEvent(status: Status) {
        updateStatusById(status.id) { oldViewData ->
            status.toViewData(
                isShowingContent = oldViewData.isShowingContent,
                isExpanded = oldViewData.isExpanded,
                isCollapsed = oldViewData.isCollapsed
            )
        }
    }

    override fun fullReload() {
        nextKey = statusData.firstOrNull { it is StatusViewData.Concrete }?.asStatusOrNull()?.id
        statusData.clear()
        currentSource?.invalidate()
    }

    override fun clearWarning(status: StatusViewData.Concrete) {
        updateActionableStatusById(status.id) {
            it.copy(filtered = emptyList())
        }
    }

    override fun saveReadingPosition(statusId: String) {
        /** Does nothing for non-cached timelines */
    }

    override suspend fun invalidate() {
        currentSource?.invalidate()
    }

    override suspend fun translate(status: StatusViewData.Concrete): NetworkResult<Unit> {
        status.copy(translation = TranslationViewData.Loading).update()
        return timelineCases.translate(status.actionableId)
            .map { translation ->
                status.copy(translation = TranslationViewData.Loaded(translation)).update()
            }
            .onFailure {
                status.update()
            }
    }

    override fun untranslate(status: StatusViewData.Concrete) {
        status.copy(translation = null).update()
    }

    @Throws(IOException::class, HttpException::class)
    suspend fun fetchStatusesForKind(
        fromId: String?,
        uptoId: String?,
        limit: Int
    ): Response<List<Status>> {
        return when (kind) {
            Kind.HOME -> api.homeTimeline(maxId = fromId, sinceId = uptoId, limit = limit)
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
            Kind.PUBLIC_TRENDING_STATUSES -> api.trendingStatuses(limit = limit, offset = fromId)
        }
    }

    private fun StatusViewData.Concrete.update() {
        val position =
            statusData.indexOfFirst { viewData -> viewData.asStatusOrNull()?.id == this.id }
        statusData[position] = this
        currentSource?.invalidate()
    }

    private inline fun updateStatusById(
        id: String,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val pos = statusData.indexOfFirst { it.asStatusOrNull()?.id == id }
        if (pos == -1) return
        updateViewDataAt(pos, updater)
    }

    private inline fun updateActionableStatusById(id: String, updater: (Status) -> Status) {
        val pos = statusData.indexOfFirst { it.asStatusOrNull()?.id == id }
        if (pos == -1) return
        updateViewDataAt(pos) { vd ->
            if (vd.status.reblog != null) {
                vd.copy(status = vd.status.copy(reblog = updater(vd.status.reblog)))
            } else {
                vd.copy(status = updater(vd.status))
            }
        }
    }

    private inline fun updateViewDataAt(
        position: Int,
        updater: (StatusViewData.Concrete) -> StatusViewData.Concrete
    ) {
        val status = statusData.getOrNull(position)?.asStatusOrNull() ?: return
        statusData[position] = updater(status)
        currentSource?.invalidate()
    }
}
