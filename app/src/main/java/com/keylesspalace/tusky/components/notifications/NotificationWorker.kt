/* Copyright 2020 Tusky Contributors
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Tusky. If
 * not, see <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky.components.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.keylesspalace.tusky.ChildWorkerFactory
import javax.inject.Inject

/** Fetch and show new notifications. */
class NotificationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val notificationsFetcher: NotificationFetcher
) : Worker(appContext, params) {
    override fun doWork(): Result {
        notificationsFetcher.fetchAndShow()
        return Result.success()
    }

    class Factory @Inject constructor(
        private val notificationsFetcher: NotificationFetcher
    ) : ChildWorkerFactory {
        override fun createWorker(appContext: Context, params: WorkerParameters): Worker {
            return NotificationWorker(appContext, params, notificationsFetcher)
        }
    }
}
