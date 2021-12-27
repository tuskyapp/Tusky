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
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.LinkHelper
import com.keylesspalace.tusky.util.inc
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject

class NetworkTimelineViewModel @Inject constructor(
    timelineCases: TimelineCases,
    private val api: MastodonApi,
    eventHub: EventHub,
    accountManager: AccountManager,
    sharedPreferences: SharedPreferences,
    filterModel: FilterModel
) : TimelineViewModel(timelineCases, api, eventHub, accountManager, sharedPreferences, filterModel) {

    var currentSource: NetworkTimelinePagingSource? = null

    val statusData: MutableList<StatusViewData> = mutableListOf()

    var nextKey: String? = null

    @ExperimentalPagingApi
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
            pagingData.filter { statusViewData ->
                !shouldFilterStatus(statusViewData)
            }
        }
        .cachedIn(viewModelScope)

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
        statusData.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            status.account.id == accountId || status.actionableStatus.account.id == accountId
        }
        currentSource?.invalidate()
    }

    override fun removeAllByInstance(instance: String) {
        statusData.removeAll { vd ->
            val status = vd.asStatusOrNull()?.status ?: return@removeAll false
            LinkHelper.getDomain(status.account.url) == instance
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
                val statusResponse = fetchStatusesForKind(
                    fromId = placeholderId.inc(),
                    uptoId = null,
                    limit = 20
                )

                val statuses = statusResponse.body()
                if (!statusResponse.isSuccessful || statuses == null) {
                    loadMoreFailed(placeholderId, HttpException(statusResponse))
                    return@launch
                }

                val activeAccount = accountManager.activeAccount!!

                val data = statuses.map { status ->
                    status.toViewData(
                        alwaysShowSensitiveMedia = !activeAccount.alwaysShowSensitiveMedia && status.actionableStatus.sensitive,
                        alwaysOpenSpoiler = activeAccount.alwaysOpenSpoiler
                    )
                }

                val index =
                    statusData.indexOfFirst { it is StatusViewData.Placeholder && it.id == placeholderId }
                statusData.removeAt(index)
                statusData.addAll(index, data)

                currentSource?.invalidate()
            } catch (e: Exception) {
                loadMoreFailed(placeholderId, e)
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
        statusData.clear()
        currentSource?.invalidate()
    }

    suspend fun fetchStatusesForKind(
        fromId: String?,
        uptoId: String?,
        limit: Int
    ): Response<List<Status>> {
        return when (kind) {
            Kind.HOME -> api.homeTimeline(fromId, uptoId, limit)
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
        }.await()
    }

    private fun StatusViewData.Concrete.update() {
        val position = statusData.indexOfFirst { viewData -> viewData.asStatusOrNull()?.id == this.id }
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

    private inline fun updateActionableStatusById(
        id: String,
        updater: (Status) -> Status
    ) {
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
