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
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelinePagingSource
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineKind
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NetworkTimelineRepository @Inject constructor(
    private val mastodonApi: MastodonApi
) {
    private var factory: InvalidatingPagingSourceFactory<String, Status>? = null

    /** @return flow of Mastodon [Status], loaded in [pageSize] increments */
    fun getStatusStream(
        kind: TimelineKind,
        pageSize: Int = PAGE_SIZE,
        initialKey: String? = null
    ): Flow<PagingData<Status>> {
        Log.d(TAG, "getStatusStream(): key: $initialKey")

        factory = InvalidatingPagingSourceFactory {
            NetworkTimelinePagingSource(mastodonApi, kind)
        }

        return Pager(
            config = PagingConfig(pageSize = pageSize),
            initialKey = initialKey,
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
