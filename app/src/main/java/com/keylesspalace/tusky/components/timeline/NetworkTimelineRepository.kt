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
import com.keylesspalace.tusky.components.timeline.viewmodel.PageCache
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getDomain
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Things that make this more difficult than it should be:
//
// - Mastodon API doesn't support "Fetch page that contains item X", you have to rely on having
//   the page that contains item X, and the previous or next page, so you can use the prev/next
//   link values from the next or previous page to step forwards or backwards to the page you
//   actually want.
// - Not all Mastodon APIs that paginate support a "Fetch me just the item X". E.g., getting a
//   list of bookmarks (https://docs.joinmastodon.org/methods/bookmarks/#get) paginates, but does
//   not support a "Get a single bookmark" call. Ditto for favourites. So even though some API
//   methods do support that they can't be used here, because this has to work for all paging APIs.
// - Values of next/prev in the Link header do not have to match any of the item keys (or be taken
//   from the same namespace).
// - Two pages that are consecutive in the result set may not have next/prev values that point
//   back to each other. I.e., this is a valid set of two pages from an API call:
//
//   .--- page index
//   |     .-- ID of last item (key in `pageCache`)
//   v     V
//   0: k: 109934818460629189, prevKey: 995916, nextKey: 941865
//   1: k: 110033940961955385, prevKey: 1073324, nextKey: 997376
//
//   They are consecutive in the result set, but pageCache[0].prevKey != pageCache[1].nextKey. So
//   there's no benefit to using the nextKey/prevKey tokens as the keys in PageCache.

/** Timeline repository where the timeline information is backed by an in-memory cache. */
class NetworkTimelineRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager
) {
    private val pageCache = PageCache()

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
            NetworkTimelinePagingSource(pageCache)
        }

        return Pager(
            config = PagingConfig(pageSize = pageSize, initialLoadSize = pageSize),
            remoteMediator = NetworkTimelineRemoteMediator(
                mastodonApi,
                accountManager,
                factory!!,
                pageCache,
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
        synchronized(pageCache) {
            for (page in pageCache.values) {
                page.data.removeAll { status ->
                    status.account.id == accountId || status.actionableStatus.account.id == accountId
                }
            }
        }
        invalidate()
    }

    fun removeAllByInstance(instance: String) {
        synchronized(pageCache) {
            for (page in pageCache.values) {
                page.data.removeAll { status -> getDomain(status.account.url) == instance }
            }
        }
        invalidate()
    }

    fun removeStatusWithId(statusId: String) {
        synchronized(pageCache) {
            pageCache.floorEntry(statusId)?.value?.data?.removeAll { status ->
                status.id == statusId || status.reblog?.id == statusId
            }
        }
        invalidate()
    }

    fun updateStatusById(statusId: String, updater: (Status) -> Status) {
        synchronized(pageCache) {
            pageCache.floorEntry(statusId)?.value?.let { page ->
                val index = page.data.indexOfFirst { it.id == statusId }
                if (index != -1) {
                    page.data[index] = updater(page.data[index])
                }
            }
        }
        invalidate()
    }

    fun updateActionableStatusById(statusId: String, updater: (Status) -> Status) {
        synchronized(pageCache) {
            pageCache.floorEntry(statusId)?.value?.let { page ->
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
        synchronized(pageCache) {
            pageCache.clear()
        }
        invalidate()
    }

    companion object {
        private const val TAG = "NetworkTimelineRepository"
        private const val PAGE_SIZE = 30
    }
}
