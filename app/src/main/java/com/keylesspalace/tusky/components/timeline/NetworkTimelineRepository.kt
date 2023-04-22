/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.timeline

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelinePagingSource
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineRemoteMediator
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.Flow
import java.util.TreeMap
import javax.inject.Inject

/** Timeline repository where the timeline information is backed by an in-memory cache. */
class NetworkTimelineRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    // TODO: This needs to be recreated if the active account changes
    private val accountManager: AccountManager,
    ) {

    /**
     * Pages of statuses.
     *
     * Each page is keyed by the ID of the first status in that page, and stores the tokens
     * use as `max_id` and `min_id` parameters in API calls to fetch pages before/after this
     * one.
     *
     * In Pager3 parlance, an "append" operation is fetching a chronologically *older* page of
     * statuses, a "prepend" operation is fetching a chronologically *newer* page of statuses.
     */
    // Storing the next/prev tokens in this structure is important, as you can't derive them from
    // status IDs (e.g., the next/prev keys returned by the "favourites" API call *do not match*
    // status IDs elsewhere). The tokens are discovered by the RemoteMediator but are used by the
    // PagingSource, so they need to be available somewhere both components can access them.
    private val pages = TreeMap<String, PagingSource.LoadResult.Page<String, Status>>()

    private var factory: InvalidatingPagingSourceFactory<String, Status>? = null

    /** @return flow of Mastodon [Status], loaded in [pageSize] increments */
    @OptIn(ExperimentalPagingApi::class)
    fun getStatusStream(
        kind: TimelineKind,
        pageSize: Int = PAGE_SIZE,
        initialKey: String? = null
    ): Flow<PagingData<Status>> {
        Log.d(TAG, "getStatusStream(): key: $initialKey")

        factory = InvalidatingPagingSourceFactory {
            NetworkTimelinePagingSource(pages)
        }

        return Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = NetworkTimelineRemoteMediator(
                mastodonApi,
                accountManager,
                factory!!,
                pages,
                kind
            ),
            pagingSourceFactory = factory!!
        ).flow
    }

    /** Invalidate the active paging source, see [PagingSource.invalidate] */
    fun invalidate() {
        factory?.invalidate()
    }

    companion object {
        private const val TAG = "NetworkTimelineRepository"
        private const val PAGE_SIZE = 30
    }
}
