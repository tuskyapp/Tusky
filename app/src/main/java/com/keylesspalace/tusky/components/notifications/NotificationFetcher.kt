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

package com.keylesspalace.tusky.components.notifications

import android.content.Context
import android.util.Log
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Marker
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isLessThan
import javax.inject.Inject

class NotificationFetcher @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    private val context: Context
) {
    fun fetchAndShow() {
        for (account in accountManager.getAllAccountsOrderedByActive()) {
            if (account.notificationsEnabled) {
                try {
                    val notifications = fetchNotifications(account)
                    notifications.forEachIndexed { index, notification ->
                        NotificationHelper.make(context, notification, account, index == 0)
                    }
                    accountManager.saveAccount(account)
                } catch (e: Exception) {
                    Log.w(TAG, "Error while fetching notifications", e)
                }
            }
        }
    }

    private fun fetchNotifications(account: AccountEntity): MutableList<Notification> {
        val authHeader = String.format("Bearer %s", account.accessToken)
        // We fetch marker to not load/show notifications which user has already seen
        val marker = fetchMarker(authHeader, account)
        if (marker != null && account.lastNotificationId.isLessThan(marker.lastReadId)) {
            account.lastNotificationId = marker.lastReadId
        }
        Log.d(TAG, "getting Notifications for " + account.fullName)
        val notifications = mastodonApi.notificationsWithAuth(
            authHeader,
            account.domain,
            account.lastNotificationId
        ).blockingGet()

        val newId = account.lastNotificationId
        var newestId = ""
        val result = mutableListOf<Notification>()
        for (notification in notifications.reversed()) {
            val currentId = notification.id
            if (newestId.isLessThan(currentId)) {
                newestId = currentId
                account.lastNotificationId = currentId
            }
            if (newId.isLessThan(currentId)) {
                result.add(notification)
            }
        }
        return result
    }

    private fun fetchMarker(authHeader: String, account: AccountEntity): Marker? {
        return try {
            val allMarkers = mastodonApi.markersWithAuth(
                authHeader,
                account.domain,
                listOf("notifications")
            ).blockingGet()
            val notificationMarker = allMarkers["notifications"]
            Log.d(TAG, "Fetched marker: $notificationMarker")
            notificationMarker
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch marker", e)
            null
        }
    }

    companion object {
        const val TAG = "NotificationFetcher"
    }
}
