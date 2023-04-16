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

// TODO: Follow lifecycle recommendations in https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
// (ditto for the other repositories).

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

    suspend fun invalidate() {
        // Invalidating when no statuses have been loaded can cause empty timelines because it
        // cancels the network load.
        if (appDatabase.timelineDao().getStatusCount(accountManager.activeAccount!!.id) < 1) {
            return
        }

        factory?.invalidate()
    }

    /** Set and store the "expanded" state of the given status, for the active account */
    suspend fun setExpanded(expanded: Boolean, statusId: String) {
        appDatabase.timelineDao().setExpanded(accountManager.activeAccount!!.id, statusId, expanded)
    }

    /** Set and store the "content showing" state of the given status, for the active account */
    suspend fun setContentShowing(showing: Boolean, statusId: String) {
        appDatabase.timelineDao()
            .setContentShowing(accountManager.activeAccount!!.id, statusId, showing)
    }

    /** Set and store the "content collapsed" ("Show more") state of the given status, for the active account */
    suspend fun setContentCollapsed(collapsed: Boolean, statusId: String) {
        appDatabase.timelineDao()
            .setContentCollapsed(accountManager.activeAccount!!.id, statusId, collapsed)
    }

    /** Remove all statuses authored/boosted by the given account, for the active account */
    suspend fun removeAllByAccountId(accountId: String) {
        appDatabase.timelineDao().removeAllByUser(accountManager.activeAccount!!.id, accountId)
    }

    /** Remove all statuses from the given instance, for the active account */
    suspend fun removeAllByInstance(instance: String) {
        appDatabase.timelineDao().deleteAllFromInstance(accountManager.activeAccount!!.id, instance)
    }

    /** Clear the warning (remove the "filtered" setting) for the given status, for the active account */
    suspend fun clearStatusWarning(statusId: String) {
        appDatabase.timelineDao().clearWarning(accountManager.activeAccount!!.id, statusId)
    }

    companion object {
        private const val TAG = "CachedTimelineRepository"
        private const val PAGE_SIZE = 30
    }
}
