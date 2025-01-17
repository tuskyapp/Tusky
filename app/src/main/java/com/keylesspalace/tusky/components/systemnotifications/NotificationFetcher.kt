package com.keylesspalace.tusky.components.systemnotifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.NewNotificationsEvent
import com.keylesspalace.tusky.components.systemnotifications.NotificationHelper.filterNotification
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Marker
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.isLessThan
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Models next/prev links from the "Links" header in an API response */
data class Links(val next: String?, val prev: String?) {
    companion object {
        fun from(linkHeader: String?): Links {
            val links = HttpHeaderLink.parse(linkHeader)
            return Links(
                next = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter(
                    "max_id"
                ),
                prev = HttpHeaderLink.findByRelationType(links, "prev")?.uri?.getQueryParameter(
                    "min_id"
                )
            )
        }
    }
}

/**
 * Fetch Mastodon notifications and show Android notifications, with summaries, for them.
 *
 * Should only be called by a worker thread.
 *
 * @see com.keylesspalace.tusky.worker.NotificationWorker
 * @see <a href="https://developer.android.com/guide/background/persistent/threading/worker">Background worker</a>
 */
@WorkerThread
class NotificationFetcher @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context,
    private val eventHub: EventHub
) {
    suspend fun fetchAndShow() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        for (account in accountManager.accounts) {
            if (account.notificationsEnabled) {
                try {
                    val notificationManager = context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                    ) as NotificationManager

                    val notificationManagerCompat = NotificationManagerCompat.from(context)

                    // Create sorted list of new notifications
                    val notifications = fetchNewNotifications(account)
                        .filter { filterNotification(notificationManager, account, it) }
                        .sortedWith(
                            compareBy({ it.id.length }, { it.id })
                        ) // oldest notifications first

                    // TODO do this before filter above? But one could argue that (for example) a tab badge is also a notification
                    //   (and should therefore adhere to the notification config).
                    eventHub.dispatch(NewNotificationsEvent(account.accountId, notifications))

                    val newNotifications = ArrayList<NotificationManagerCompat.NotificationWithIdAndTag>()

                    val notificationsByType: Map<Notification.Type, List<Notification>> = notifications.groupBy { it.type }
                    notificationsByType.forEach { notificationsGroup: Map.Entry<Notification.Type, List<Notification>> ->
                        // NOTE Enqueue the summary first: Needed to avoid rate limit problems:
                        //   ie. single notification is enqueued but that later summary one is filtered and thus no grouping
                        //   takes place.
                        newNotifications.add(
                            NotificationHelper.makeSummaryNotification(
                                context,
                                notificationManager,
                                account,
                                notificationsGroup.key,
                                notificationsGroup.value
                            )
                        )

                        notificationsGroup.value.forEach { notification ->
                            newNotifications.add(
                                NotificationHelper.make(
                                    context,
                                    notificationManager,
                                    notification,
                                    account
                                )
                            )
                        }
                    }

                    // NOTE having multiple summary notifications this here should still collapse them in only one occurrence
                    notificationManagerCompat.notify(newNotifications)
                } catch (e: Exception) {
                    Log.e(TAG, "Error while fetching notifications", e)
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
     * The "water mark" for Mastodon Notification IDs are stored in three places.
     *
     * - acccount.lastNotificationId -- the ID of the top-most notification when the user last
     *   left the Notifications tab.
     * - The Mastodon "marker" API -- the ID of the most recent notification fetched here.
     * - account.notificationMarkerId -- local version of the value from the Mastodon marker
     *   API, in case the Mastodon server does not implement that API.
     *
     * The user may have refreshed the "Notifications" tab and seen notifications newer than the
     * ones that were last fetched here. So `lastNotificationId` takes precedence if it is greater
     * than the marker.
     */
    private suspend fun fetchNewNotifications(account: AccountEntity): List<Notification> {
        val authHeader = "Bearer ${account.accessToken}"

        // Figure out where to read from. Choose the most recent notification ID from:
        //
        // - The Mastodon marker API (if the server supports it)
        // - account.notificationMarkerId
        // - account.lastNotificationId
        Log.d(TAG, "getting notification marker for ${account.fullName}")
        val remoteMarkerId = fetchMarker(authHeader, account)?.lastReadId ?: "0"
        val localMarkerId = account.notificationMarkerId
        val markerId = if (remoteMarkerId.isLessThan(
                localMarkerId
            )
        ) {
            localMarkerId
        } else {
            remoteMarkerId
        }
        val readingPosition = account.lastNotificationId

        var minId: String? = if (readingPosition.isLessThan(markerId)) markerId else readingPosition
        Log.d(TAG, "  remoteMarkerId: $remoteMarkerId")
        Log.d(TAG, "  localMarkerId: $localMarkerId")
        Log.d(TAG, "  readingPosition: $readingPosition")

        Log.d(TAG, "getting Notifications for ${account.fullName}, min_id: $minId")

        // Fetch all outstanding notifications
        val notifications = buildList {
            while (minId != null) {
                val response = mastodonApi.notificationsWithAuth(
                    authHeader,
                    account.domain,
                    minId = minId
                )
                if (!response.isSuccessful) break

                // Notifications are returned in the page in order, newest first,
                // (https://github.com/mastodon/documentation/issues/1226), insert the
                // new page at the head of the list.
                response.body()?.let { addAll(0, it) }

                // Get the previous page, which will be chronologically newer
                // notifications. If it doesn't exist this is null and the loop
                // will exit.
                val links = Links.from(response.headers()["link"])
                minId = links.prev
            }
        }

        // Save the newest notification ID in the marker.
        notifications.firstOrNull()?.let {
            val newMarkerId = notifications.first().id
            Log.d(TAG, "updating notification marker for ${account.fullName} to: $newMarkerId")
            mastodonApi.updateMarkersWithAuth(
                auth = authHeader,
                domain = account.domain,
                notificationsLastReadId = newMarkerId
            )
            accountManager.updateAccount(account) { copy(notificationMarkerId = newMarkerId) }
        }

        return notifications
    }

    private suspend fun fetchMarker(authHeader: String, account: AccountEntity): Marker? {
        return try {
            val allMarkers = mastodonApi.markersWithAuth(
                authHeader,
                account.domain,
                listOf("notifications")
            )
            val notificationMarker = allMarkers["notifications"]
            Log.d(TAG, "Fetched marker for ${account.fullName}: $notificationMarker")
            notificationMarker
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch marker", e)
            null
        }
    }

    companion object {
        private const val TAG = "NotificationFetcher"
    }
}
