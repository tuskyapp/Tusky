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

package com.keylesspalace.tusky.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.keylesspalace.tusky.components.notifications.NotificationFetcher
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
