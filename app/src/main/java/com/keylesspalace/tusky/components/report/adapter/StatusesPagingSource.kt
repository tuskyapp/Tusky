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

package com.keylesspalace.tusky.components.report.adapter

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext

class StatusesPagingSource(
    private val accountId: String,
    private val mastodonApi: MastodonApi
) : PagingSource<String, Status>() {

    override fun getRefreshKey(state: PagingState<String, Status>): String? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestItemToPosition(anchorPosition)?.id
        }
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Status> {
        val key = params.key
        try {
            val result = if (params is LoadParams.Refresh && key != null) {
                withContext(Dispatchers.IO) {
                    val initialStatus = async { getSingleStatus(key) }
                    val additionalStatuses = async { getStatusList(maxId = key, limit = params.loadSize - 1) }
                    listOf(initialStatus.await()) + additionalStatuses.await()
                }
            } else {
                val maxId = if (params is LoadParams.Refresh || params is LoadParams.Append) {
                    params.key
                } else {
                    null
                }

                val minId = if (params is LoadParams.Prepend) {
                    params.key
                } else {
                    null
                }

                getStatusList(minId = minId, maxId = maxId, limit = params.loadSize)
            }
            return LoadResult.Page(
                data = result,
                prevKey = result.firstOrNull()?.id,
                nextKey = result.lastOrNull()?.id
            )
        } catch (e: Exception) {
            Log.w("StatusesPagingSource", "failed to load statuses", e)
            return LoadResult.Error(e)
        }
    }

    private suspend fun getSingleStatus(statusId: String): Status {
        return mastodonApi.statusObservable(statusId).await()
    }

    private suspend fun getStatusList(minId: String? = null, maxId: String? = null, limit: Int): List<Status> {
        return mastodonApi.accountStatusesObservable(
            accountId = accountId,
            maxId = maxId,
            sinceId = null,
            minId = minId,
            limit = limit,
            excludeReblogs = true
        ).await()
    }
}
