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

import android.app.Notification
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.systemnotifications.NotificationFetcher
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Fetch and show new notifications. */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val notificationsFetcher: NotificationFetcher,
    notificationService: NotificationService,
) : CoroutineWorker(appContext, params) {
    val notification: Notification = notificationService.createWorkerNotification(
        R.string.notification_notification_worker
    )

    override suspend fun doWork(): Result {
        notificationsFetcher.fetchAndShow()
        return Result.success()
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(
        NotificationService.NOTIFICATION_ID_FETCH_NOTIFICATION,
        notification
    )
}
