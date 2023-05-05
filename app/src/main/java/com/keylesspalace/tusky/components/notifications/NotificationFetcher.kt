package com.keylesspalace.tusky.components.notifications

import android.content.Context
import android.util.Log
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Marker
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
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

    /**
     * Fetch new Mastodon Notifications and update the marker position.
     *
     * Here, "new" means "notifications with IDs newer than notifications the user has already
     * seen."
     *
     * The "water mark" for Mastodon Notification IDs are stored in two places.
     *
     * - acccount.lastNotificationId -- the ID of the top-most notification when the user last
     *   left the Notifications tab.
     * - The Mastodon "marker" API -- the ID of the most recent notification fetched here.
     *
     * The user may have refreshed the "Notifications" tab and seen notifications newer than the
     * ones that were last fetched here. So `lastNotificationId` takes precedence if it is greater
     * than the marker.
     */
    private fun fetchNotifications(account: AccountEntity): MutableList<Notification> {
        val authHeader = String.format("Bearer %s", account.accessToken)

        val minId = when (val marker = fetchMarker(authHeader, account)) {
            null -> account.lastNotificationId.takeIf { it != "0" }
            else -> if (marker.lastReadId > account.lastNotificationId) marker.lastReadId else account.lastNotificationId
        }

        Log.d(TAG, "getting Notifications for ${account.fullName}, min_id: $minId")

        val notifications = mastodonApi.notificationsWithAuth(
            authHeader,
            account.domain,
            minId
        ).blockingGet()

        if (notifications.isEmpty()) return mutableListOf()

        // Notifications are returned in order, most recent first. Save the newest notification ID
        // in the marker.
        val newMarkerId = notifications.first().id
        Log.d(TAG, "updating notification marker to: $newMarkerId")
        mastodonApi.updateMarkersWithAuth(authHeader, notificationsLastReadId = newMarkerId)

        return notifications.toMutableList()
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
