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

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import retrofit2.HttpException

@OptIn(ExperimentalPagingApi::class)
class NetworkTimelineRemoteMediator(
    private val accountManager: AccountManager,
    private val viewModel: NetworkTimelineViewModel
) : RemoteMediator<String, StatusViewData>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, StatusViewData>
    ): MediatorResult {

        try {
            val statusResponse = when (loadType) {
                LoadType.REFRESH -> {
                    viewModel.fetchStatusesForKind(null, null, limit = state.config.pageSize)
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val maxId = viewModel.nextKey
                    if (maxId != null) {
                        viewModel.fetchStatusesForKind(maxId, null, limit = state.config.pageSize)
                    } else {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }
                }
            }

            val statuses = statusResponse.body()
            if (!statusResponse.isSuccessful || statuses == null) {
                return MediatorResult.Error(HttpException(statusResponse))
            }

            val activeAccount = accountManager.activeAccount!!

            val data = statuses.map { status ->

                val oldStatus = viewModel.statusData.find { s ->
                    s.asStatusOrNull()?.id == status.id
                }?.asStatusOrNull()

                val contentShowing = oldStatus?.isShowingContent ?: activeAccount.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive
                val expanded = oldStatus?.isExpanded ?: activeAccount.alwaysOpenSpoiler
                val contentCollapsed = oldStatus?.isCollapsed ?: true

                status.toViewData(
                    isShowingContent = contentShowing,
                    isExpanded = expanded,
                    isCollapsed = contentCollapsed
                )
            }

            if (loadType == LoadType.REFRESH && viewModel.statusData.isNotEmpty()) {

                val insertPlaceholder = if (statuses.isNotEmpty()) {
                    !viewModel.statusData.removeAll { statusViewData ->
                        statuses.any { status -> status.id == statusViewData.asStatusOrNull()?.id }
                    }
                } else {
                    false
                }

                viewModel.statusData.addAll(0, data)

                if (insertPlaceholder) {
                    viewModel.statusData[statuses.size - 1] = StatusViewData.Placeholder(statuses.last().id, false)
                }
            } else {
                val linkHeader = statusResponse.headers()["Link"]
                val links = HttpHeaderLink.parse(linkHeader)
                val nextId = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

                viewModel.nextKey = nextId

                viewModel.statusData.addAll(data)
            }

            viewModel.currentSource?.invalidate()
            return MediatorResult.Success(endOfPaginationReached = statuses.isEmpty())
        } catch (e: Exception) {
            return ifExpected(e) {
                MediatorResult.Error(e)
            }
        }
    }
}
