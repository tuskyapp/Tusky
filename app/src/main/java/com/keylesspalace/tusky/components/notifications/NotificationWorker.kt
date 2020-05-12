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
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isLessThan
import java.io.IOException
import javax.inject.Inject

class NotificationWorker(
        private val context: Context,
        params: WorkerParameters,
        private val mastodonApi: MastodonApi,
        private val accountManager: AccountManager
) : Worker(context, params) {

    override fun doWork(): Result {
        val accountList = accountManager.getAllAccountsOrderedByActive()
        for (account in accountList) {
            if (account.notificationsEnabled) {
                try {
                    Log.d(TAG, "getting Notifications for " + account.fullName)
                    val notificationsResponse = mastodonApi.notificationsWithAuth(
                            String.format("Bearer %s", account.accessToken),
                            account.domain
                    ).execute()
                    val notifications = notificationsResponse.body()
                    if (notificationsResponse.isSuccessful && notifications != null) {
                        onNotificationsReceived(account, notifications)
                    } else {
                        Log.w(TAG, "error receiving notifications")
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "error receiving notifications", e)
                }
            }
        }
        return Result.success()
    }

    private fun onNotificationsReceived(account: AccountEntity, notificationList: List<Notification>) {
        val newId = account.lastNotificationId
        var newestId = ""
        var isFirstOfBatch = true
        notificationList.reversed().forEach { notification ->
            val currentId = notification.id
            if (newestId.isLessThan(currentId)) {
                newestId = currentId
            }
            if (newId.isLessThan(currentId)) {
                NotificationHelper.make(context, notification, account, isFirstOfBatch)
                isFirstOfBatch = false
            }
        }
        account.lastNotificationId = newestId
        accountManager.saveAccount(account)
    }

    companion object {
        private const val TAG = "NotificationWorker"
    }

}

class NotificationWorkerFactory @Inject constructor(
        val api: MastodonApi,
        val accountManager: AccountManager
): WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
        if(workerClassName == NotificationWorker::class.java.name) {
            return NotificationWorker(appContext, workerParameters, api, accountManager)
        }
        return null
    }
}
