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
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
    private val notificationsFetcher: NotificationFetcher
) : Worker(context, params) {

    override fun doWork(): Result {
        notificationsFetcher.fetchAndShow()
        return Result.success()
    }
}

class NotificationWorkerFactory @Inject constructor(
    private val notificationsFetcher: NotificationFetcher
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        if (workerClassName == NotificationWorker::class.java.name) {
            return NotificationWorker(appContext, workerParameters, notificationsFetcher)
        }
        return null
    }
}
