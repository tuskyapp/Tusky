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

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.keylesspalace.tusky.ChildWorkerFactory
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import javax.inject.Inject

/** Prune the database cache of old statuses. */
class PruneCacheWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val appDatabase: AppDatabase,
    private val accountManager: AccountManager
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        accountManager.activeAccount?.id?.let { accountId ->
            Log.d(TAG, "Pruning database using account ID: $accountId")
            appDatabase.timelineDao().cleanup(accountId, MAX_STATUSES_IN_CACHE)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "PruneCacheWorker"
        private const val MAX_STATUSES_IN_CACHE = 1000
        const val PERIODIC_WORK_TAG = "PruneCacheWorker_periodic"
    }

    class Factory @Inject constructor(
        private val appDatabase: AppDatabase,
        private val accountManager: AccountManager
    ) : ChildWorkerFactory {
        override fun createWorker(appContext: Context, params: WorkerParameters): ListenableWorker {
            return PruneCacheWorker(appContext, params, appDatabase, accountManager)
        }
    }
}
