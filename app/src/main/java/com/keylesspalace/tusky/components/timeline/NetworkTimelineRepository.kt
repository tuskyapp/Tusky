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
import androidx.annotation.VisibleForTesting
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
import com.keylesspalace.tusky.util.getDomain
import kotlinx.coroutines.flow.Flow
import java.util.TreeMap
import javax.inject.Inject

/** A page of data from the Mastodon API */
data class Page<Key : Any, Value : Any> constructor(
    /** Loaded data */
    val data: MutableList<Value>,
    /**
     * [Key] for previous page (newer results, PREPEND operation) if more data can be loaded in
     * that direction, `null` otherwise.
     */
    val prevKey: Key? = null,
    /**
     * [Key] for next page (older results, APPEND operation) if more data can be loaded in that
     * direction, `null` otherwise.
     */
    val nextKey: Key? = null
)

/** Timeline repository where the timeline information is backed by an in-memory cache. */
class NetworkTimelineRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager
) {

    /**
     * Cached pages of statuses.
     *
     * Each page is keyed by the ID of the first status in that page, and stores the tokens
     * use as `max_id` and `min_id` parameters in API calls to fetch pages before/after this
     * one.
     *
     * In Pager3 parlance, an "append" operation is fetching a chronologically *older* page of
     * statuses using `nextKey`, a "prepend" operation is fetching a chronologically *newer*
     * page of statuses using `prevKey`.
     */
    // Storing the next/prev tokens in this structure is important, as you can't derive them from
    // status IDs (e.g., the next/prev keys returned by the "favourites" API call *do not match*
    // status IDs elsewhere). The tokens are discovered by the RemoteMediator but are used by the
    // PagingSource, so they need to be available somewhere both components can access them.
    private val pages = makeEmptyPageCache()

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

    fun removeAllByAccountId(accountId: String) {
        synchronized(pages) {
            for (page in pages.values) {
                page.data.removeAll { status ->
                    status.account.id == accountId || status.actionableStatus.account.id == accountId
                }
            }
        }
        invalidate()
    }

    fun removeAllByInstance(instance: String) {
        synchronized(pages) {
            for (page in pages.values) {
                page.data.removeAll { status -> getDomain(status.account.url) == instance }
            }
        }
        invalidate()
    }

    fun removeStatusWithId(statusId: String) {
        synchronized(pages) {
            pages.floorEntry(statusId)?.value?.data?.removeAll { status ->
                status.id == statusId || status.reblog?.id == statusId
            }
        }
        invalidate()
    }

    fun updateStatusById(statusId: String, updater: (Status) -> Status) {
        synchronized(pages) {
            pages.floorEntry(statusId)?.value?.let { page ->
                val index = page.data.indexOfFirst { it.id == statusId }
                if (index != -1) {
                    page.data[index] = updater(page.data[index])
                }
            }
        }
        invalidate()
    }

    fun updateActionableStatusById(statusId: String, updater: (Status) -> Status) {
        synchronized(pages) {
            pages.floorEntry(statusId)?.value?.let { page ->
                val index = page.data.indexOfFirst { it.id == statusId }
                if (index != -1) {
                    val status = page.data[index]
                    if (status.reblog != null) {
                        page.data[index] = status.copy(reblog = updater(status.reblog))
                    } else {
                        page.data[index] = updater(status)
                    }
                }
            }
        }
    }

    fun reload() {
        synchronized(pages) {
            pages.clear()
        }
        invalidate()
    }

    companion object {
        private const val TAG = "NetworkTimelineRepository"
        private const val PAGE_SIZE = 30

        /**
         * Creates an empty page cache with a comparator that ensures keys are compared first
         * by length, then by natural order.
         *
         * The map key is the ID of the newest status in the page it maps to.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun makeEmptyPageCache() = TreeMap<String, Page<String, Status>>(compareBy({ it.length }, { it }))
    }
}
