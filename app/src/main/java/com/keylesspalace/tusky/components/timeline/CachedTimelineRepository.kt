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
import com.google.gson.Gson
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineRemoteMediator
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineKind
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.EmptyPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// TODO: This is very similar to NetworkTimelineRepository. They could be merged (and the use
// of the cache be made a parameter to getStatusStream), except that they return Pagers of
// different generic types.
//
// NetworkTimelineRepository factory is <String, Status>, this is <Int, TimelineStatusWithAccount>
//
// Re-writing the caching so that they can use the same types is the TODO.

class CachedTimelineRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    private val appDatabase: AppDatabase,
    private val gson: Gson
) {
    private var factory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>? = null

    /** @return flow of Mastodon [TimelineStatusWithAccount], loaded in [pageSize] increments */
    @OptIn(ExperimentalPagingApi::class)
    fun getStatusStream(
        kind: TimelineKind,
        pageSize: Int = PAGE_SIZE,
        initialKey: String? = null
    ): Flow<PagingData<TimelineStatusWithAccount>> {
        Log.d(TAG, "getStatusStream(): key: $initialKey")

        factory = InvalidatingPagingSourceFactory {
            val activeAccount = accountManager.activeAccount
            if (activeAccount == null) {
                EmptyPagingSource()
            } else {
                appDatabase.timelineDao().getStatuses(activeAccount.id)
            }
        }

        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE),
            remoteMediator = CachedTimelineRemoteMediator(accountManager, mastodonApi, appDatabase, gson),
            pagingSourceFactory = factory!!
        ).flow
    }

    fun invalidate() {
        factory?.invalidate()
    }

    companion object {
        private const val TAG = "CachedTimelineRepository"
        private const val PAGE_SIZE = 30
    }
}
