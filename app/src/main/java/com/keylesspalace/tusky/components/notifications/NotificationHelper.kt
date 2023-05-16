/* Copyright 2018 Jeremiasz Nelz <remi6397(a)gmail.com>
 * Copyright 2017 Andrew Dawson
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

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.annotation.CheckResult
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.keylesspalace.tusky.BuildConfig.APPLICATION_ID
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.startIntent
import com.keylesspalace.tusky.components.compose.ComposeActivity.ComposeOptions
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.receiver.SendStatusBroadcastReceiver
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.buildDescription
import com.keylesspalace.tusky.viewdata.calculatePercent
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Notification channels used in Android O+ */
enum class TuskyNotificationChannel(private val nameResource: Int, private val descriptionResource: Int) {
    CHANNEL_MENTION(R.string.notification_mention_name, R.string.notification_mention_descriptions),
    CHANNEL_FOLLOW(R.string.notification_follow_name, R.string.notification_follow_description),
    CHANNEL_FOLLOW_REQUEST(R.string.notification_follow_request_name, R.string.notification_follow_request_description),
    CHANNEL_BOOST(R.string.notification_boost_name, R.string.notification_boost_description),
    CHANNEL_FAVOURITE(R.string.notification_favourite_name, R.string.notification_favourite_description),
    CHANNEL_POLL(R.string.notification_poll_name, R.string.notification_poll_description),
    CHANNEL_SUBSCRIPTIONS(R.string.notification_subscription_name, R.string.notification_subscription_description),
    CHANNEL_SIGN_UP(R.string.notification_sign_up_name, R.string.notification_sign_up_description),
    CHANNEL_UPDATES(R.string.notification_update_name, R.string.notification_update_description),
    CHANNEL_REPORT(R.string.notification_report_name, R.string.notification_report_description);

    /**
     * Creates an Android [NotificationChannel] from the specification.
     *
     * The [NotificationChannel] object is created but must still be passed to
     * [NotificationManager.createNotificationChannel] or similar to actually create the
     * channel.
     *
     * @param context Context to use when fetching string resources
     * @param group Set the created channel's group
     */
    @CheckResult(suggest="NotificationManager.createNotificationChannel(NotificationChannel)")
    fun makeNotificationChannel(context: Context, group: String): NotificationChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val channelId = channelId(group)
        val name = context.getString(nameResource)
        val description = context.getString(descriptionResource)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance)
        channel.description = description
        channel.enableLights(true)
        channel.lightColor = -0xd46f27
        channel.enableVibration(true)
        channel.setShowBadge(true)
        channel.group = group
        return channel
    }

    /**
     * Create the channel's full ID, using the value of [group].
     */
    fun channelId(group: String): String = name + group

    companion object {
        fun from(ty: Notification.Type): TuskyNotificationChannel? = when (ty) {
            Notification.Type.UNKNOWN -> null
            Notification.Type.MENTION -> CHANNEL_MENTION
            Notification.Type.REBLOG -> CHANNEL_BOOST
            Notification.Type.FAVOURITE -> CHANNEL_FAVOURITE
            Notification.Type.FOLLOW -> CHANNEL_FOLLOW
            Notification.Type.FOLLOW_REQUEST -> CHANNEL_FOLLOW_REQUEST
            Notification.Type.POLL -> CHANNEL_POLL
            Notification.Type.STATUS -> CHANNEL_SUBSCRIPTIONS
            Notification.Type.SIGN_UP -> CHANNEL_SIGN_UP
            Notification.Type.UPDATE -> CHANNEL_UPDATES
            Notification.Type.REPORT -> CHANNEL_REPORT
        }
    }
}

object NotificationHelper {
    private var notificationId = 0

    /**
     * constants used in Intents
     */
    const val ACCOUNT_ID = "account_id"
    const val TYPE = "$APPLICATION_ID.notification.type"
    private const val TAG = "NotificationHelper"
    const val REPLY_ACTION = "REPLY_ACTION"
    const val KEY_REPLY = "KEY_REPLY"
    const val KEY_SENDER_ACCOUNT_ID = "$APPLICATION_ID.KEY_SENDER_ACCOUNT_ID"
    const val KEY_SENDER_ACCOUNT_IDENTIFIER = "$APPLICATION_ID.KEY_SENDER_ACCOUNT_IDENTIFIER"
    const val KEY_SENDER_ACCOUNT_FULL_NAME = "$APPLICATION_ID.KEY_SENDER_ACCOUNT_FULL_NAME"
    const val KEY_NOTIFICATION_ID = "$APPLICATION_ID.KEY_NOTIFICATION_ID"
    const val KEY_CITED_STATUS_ID = "$APPLICATION_ID.KEY_CITED_STATUS_ID"
    const val KEY_VISIBILITY = "$APPLICATION_ID.KEY_VISIBILITY"
    const val KEY_SPOILER = "$APPLICATION_ID.KEY_SPOILER"
    const val KEY_MENTIONS = "$APPLICATION_ID.KEY_MENTIONS"

    /**
     * WorkManager Tag
     */
    private const val NOTIFICATION_PULL_TAG = "pullNotifications"

    /** Tag for the summary notification  */
    private const val GROUP_SUMMARY_TAG = "$APPLICATION_ID.notification.group_summary"

    /** The name of the account that caused the notification, for use in a summary  */
    private const val EXTRA_ACCOUNT_NAME =
        "$APPLICATION_ID.notification.extra.account_name"

    /** The notification's type (string representation of a Notification.Type)  */
    private const val EXTRA_NOTIFICATION_TYPE =
        "$APPLICATION_ID.notification.extra.notification_type"

    /**
     * Takes a given Mastodon notification and creates a new Android notification or updates the
     * existing Android notification.
     *
     *
     * The Android notification has it's tag set to the Mastodon notification ID, and it's ID set
     * to the ID of the account that received the notification.
     *
     * @param context to access application preferences and services
     * @param notification    a new Mastodon notification
     * @param account the account for which the notification should be shown
     * @return the new notification
     */
    fun make(
        context: Context,
        notificationManager: NotificationManager,
        notification: Notification,
        account: AccountEntity,
        isFirstOfBatch: Boolean
    ): android.app.Notification {
        val body = notification.rewriteToStatusTypeIfNeeded(account.accountId)
        val mastodonNotificationId = body.id
        val accountId = account.id.toInt()

        // Check for an existing notification with this Mastodon Notification ID
        var existingAndroidNotification: android.app.Notification? = null
        for (androidNotification in notificationManager.activeNotifications) {
            if (mastodonNotificationId == androidNotification.tag && accountId == androidNotification.id) {
                existingAndroidNotification = androidNotification.notification
            }
        }

        // Notification group member
        // =========================
        notificationId++

        // Create the notification -- either create a new one, or use the existing one.
        val builder: NotificationCompat.Builder = if (existingAndroidNotification == null) {
            newAndroidNotification(context, body, account)
        } else {
            NotificationCompat.Builder(context, existingAndroidNotification)
        }
        builder.setContentTitle(titleForType(context, body, account))
            .setContentText(bodyForType(body, context, account.alwaysOpenSpoiler))
        if (body.type === Notification.Type.MENTION || body.type === Notification.Type.POLL) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bodyForType(body, context, account.alwaysOpenSpoiler))
            )
        }

        // load the avatar synchronously
        val accountAvatar = try {
            Glide.with(context)
                .asBitmap()
                .load(body.account.avatar)
                .transform(RoundedCorners(20))
                .submit()
                .get()
        } catch (e: ExecutionException) {
            Log.d(TAG, "error loading account avatar", e)
            BitmapFactory.decodeResource(context.resources, R.drawable.avatar_default)
        } catch (e: InterruptedException) {
            Log.d(TAG, "error loading account avatar", e)
            BitmapFactory.decodeResource(context.resources, R.drawable.avatar_default)
        }
        builder.setLargeIcon(accountAvatar)

        // Reply to mention action; RemoteInput is available from KitKat Watch, but buttons are available from Nougat
        if (body.type === Notification.Type.MENTION &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ) {
            val replyRemoteInput = RemoteInput.Builder(KEY_REPLY)
                .setLabel(context.getString(R.string.label_quick_reply))
                .build()
            val quickReplyPendingIntent = getStatusReplyIntent(context, body, account)
            val quickReplyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply_24dp,
                context.getString(R.string.action_quick_reply),
                quickReplyPendingIntent
            )
                .addRemoteInput(replyRemoteInput)
                .build()
            builder.addAction(quickReplyAction)
            val composeIntent = getStatusComposeIntent(context, body, account)
            val composeAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply_24dp,
                context.getString(R.string.action_compose_shortcut),
                composeIntent
            )
                .setShowsUserInterface(true)
                .build()
            builder.addAction(composeAction)
        }
        builder.setSubText(account.fullName)
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
        builder.setOnlyAlertOnce(true)

        val extras = Bundle()
        // Add the sending account's name, so it can be used when summarising this notification
        extras.putString(EXTRA_ACCOUNT_NAME, body.account.name)
        extras.putString(EXTRA_NOTIFICATION_TYPE, body.type.toString())
        builder.addExtras(extras)

        // Only alert for the first notification of a batch to avoid multiple alerts at once
        if (!isFirstOfBatch) {
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }
        return builder.build()
    }

    /**
     * Updates the summary notifications for each notification group.
     *
     *
     * Notifications are sent to channels. Within each channel they may be grouped, and the group
     * may have a summary.
     *
     *
     * Tusky uses N notification channels for each account, each channel corresponds to a type
     * of notification (follow, reblog, mention, etc). Therefore each channel also has exactly
     * 0 or 1 summary notifications along with its regular notifications.
     *
     *
     * The group key is the same as the channel ID.
     *
     *
     * Regnerates the summary notifications for all active Tusky notifications for `account`.
     * This may delete the summary notification if there are no active notifications for that
     * account in a group.
     *
     * @see [Create a
     * notification group](https://developer.android.com/develop/ui/views/notifications/group)
     *
     * @param context to access application preferences and services
     * @param notificationManager the system's NotificationManager
     * @param account the account for which the notification should be shown
     */
    fun updateSummaryNotifications(
        context: Context,
        notificationManager: NotificationManager,
        account: AccountEntity
    ) {
        // Map from the channel ID to a list of notifications in that channel. Those are the
        // notifications that will be summarised.
        val channelGroups: MutableMap<String?, MutableList<StatusBarNotification>> = HashMap()
        val accountId = account.id.toInt()

        // Initialise the map with all channel IDs.
        for (ty in Notification.Type.values()) {
            channelGroups[getChannelId(account, ty)] = ArrayList()
        }

        // Fetch all existing notifications. Add them to the map, ignoring notifications that:
        // - belong to a different account
        // - are summary notifications
        notificationManager.activeNotifications
            .filter { it.id == accountId }
            .filter {
                // TODO: API 26 supports getting the channel ID directly (sn.getNotification().getChannelId()).
                // This works here because the channelId and the groupKey are the same.
                val channelId = it.notification.group
                val summaryTag = "$GROUP_SUMMARY_TAG.$channelId"
                it.tag != summaryTag
            }
            .forEach { channelGroups[it.notification.group]?.add(it) }

        // Create, update, or cancel the summary notifications for each group.
        for ((channelId, members) in channelGroups) {
            val summaryTag = "$GROUP_SUMMARY_TAG.$channelId"

            // If there are 0-1 notifications in this group then the additional summary
            // notification is not needed and can be cancelled.
            if (members.size <= 1) {
                notificationManager.cancel(summaryTag, accountId)
                continue
            }

            // Create a notification that summarises the other notifications in this group

            // All notifications in this group have the same type, so get it from the first.
            val notificationType = members[0].notification.extras.getString(
                EXTRA_NOTIFICATION_TYPE
            )
            val summaryResultIntent = Intent(context, MainActivity::class.java)
                .putExtra(ACCOUNT_ID, accountId.toLong())
                .putExtra(TYPE, notificationType)

            val summaryStackBuilder = TaskStackBuilder.create(context)
                .addParentStack(MainActivity::class.java)
                .addNextIntent(summaryResultIntent)

            val summaryResultPendingIntent = summaryStackBuilder.getPendingIntent(
                (notificationId + account.id * 10000).toInt(),
                pendingIntentFlags(false)
            )
            val title = context.resources.getQuantityString(
                R.plurals.notification_title_summary,
                members.size,
                members.size
            )
            val text = joinNames(context, members)
            val summaryBuilder = NotificationCompat.Builder(context, channelId!!)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(summaryResultPendingIntent)
                .setColor(context.getColor(R.color.notification_color))
                .setAutoCancel(true)
                .setShortcutId(account.id.toString())
                .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(account.fullName)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setOnlyAlertOnce(true)
                .setGroup(channelId)
                .setGroupSummary(true)
            setSoundVibrationLight(account, summaryBuilder)

            // TODO: Use the batch notification API available in NotificationManagerCompat
            // 1.11 and up (https://developer.android.com/jetpack/androidx/releases/core#1.11.0-alpha01)
            // when it is released.
            notificationManager.notify(summaryTag, accountId, summaryBuilder.build())

            // Android will rate limit / drop notifications if they're posted too
            // quickly. There is no indication to the user that this happened.
            // See https://github.com/tuskyapp/Tusky/pull/3626#discussion_r1192963664
            Thread.sleep(1000)
        }
    }

    private fun newAndroidNotification(
        context: Context,
        body: Notification,
        account: AccountEntity
    ): NotificationCompat.Builder {
        // we have to switch account here
        val eventResultIntent = Intent(context, MainActivity::class.java)
            .putExtra(ACCOUNT_ID, account.id)
            .putExtra(TYPE, body.type.name)

        val eventStackBuilder = TaskStackBuilder.create(context)
            .addParentStack(MainActivity::class.java)
            .addNextIntent(eventResultIntent)

        val eventResultPendingIntent = eventStackBuilder.getPendingIntent(
            account.id.toInt(),
            pendingIntentFlags(false)
        )

        val channelId = getChannelId(account, body)!!
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(eventResultPendingIntent)
            .setColor(context.getColor(R.color.notification_color))
            .setGroup(channelId)
            .setAutoCancel(true)
            .setShortcutId(java.lang.Long.toString(account.id))
            .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
        setSoundVibrationLight(account, builder)
        return builder
    }

    private fun getStatusReplyIntent(
        context: Context,
        body: Notification,
        account: AccountEntity
    ): PendingIntent {
        val status = body.status
        val inReplyToId = status!!.id
        val (_, _, account1, _, _, _, _, _, _, _, _, _, _, _, _, _, _, contentWarning, replyVisibility, _, mentions) = status.actionableStatus
        var mentionedUsernames: MutableList<String?> = ArrayList()
        mentionedUsernames.add(account1.username)
        for ((_, _, username) in mentions) {
            mentionedUsernames.add(username)
        }
        mentionedUsernames.removeAll(setOf(account.username))
        mentionedUsernames = ArrayList(LinkedHashSet(mentionedUsernames))
        val replyIntent = Intent(context, SendStatusBroadcastReceiver::class.java)
            .setAction(REPLY_ACTION)
            .putExtra(KEY_SENDER_ACCOUNT_ID, account.id)
            .putExtra(KEY_SENDER_ACCOUNT_IDENTIFIER, account.identifier)
            .putExtra(KEY_SENDER_ACCOUNT_FULL_NAME, account.fullName)
            .putExtra(KEY_NOTIFICATION_ID, notificationId)
            .putExtra(KEY_CITED_STATUS_ID, inReplyToId)
            .putExtra(KEY_VISIBILITY, replyVisibility)
            .putExtra(KEY_SPOILER, contentWarning)
            .putExtra(KEY_MENTIONS, mentionedUsernames.toTypedArray())
        return PendingIntent.getBroadcast(
            context.applicationContext,
            notificationId,
            replyIntent,
            pendingIntentFlags(true)
        )
    }

    private fun getStatusComposeIntent(
        context: Context,
        body: Notification,
        account: AccountEntity
    ): PendingIntent {
        val status = body.status
        val citedLocalAuthor = status!!.account.localUsername
        val citedText = status.content.parseAsMastodonHtml().toString()
        val inReplyToId = status.id
        val (_, _, account1, _, _, _, _, _, _, _, _, _, _, _, _, _, _, contentWarning, replyVisibility, _, mentions, _, _, _, _, _, _, language) = status.actionableStatus
        val mentionedUsernames: MutableSet<String> = LinkedHashSet()
        mentionedUsernames.add(account1.username)
        for ((_, _, mentionedUsername) in mentions) {
            if (mentionedUsername != account.username) {
                mentionedUsernames.add(mentionedUsername)
            }
        }
        val composeOptions = ComposeOptions()
        composeOptions.inReplyToId = inReplyToId
        composeOptions.replyVisibility = replyVisibility
        composeOptions.contentWarning = contentWarning
        composeOptions.replyingStatusAuthor = citedLocalAuthor
        composeOptions.replyingStatusContent = citedText
        composeOptions.mentionedUsernames = mentionedUsernames
        composeOptions.modifiedInitialState = true
        composeOptions.language = language
        composeOptions.kind = ComposeActivity.ComposeKind.NEW
        val composeIntent = startIntent(
            context,
            composeOptions,
            notificationId,
            account.id
        )
        composeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context.applicationContext,
            notificationId,
            composeIntent,
            pendingIntentFlags(false)
        )
    }

    fun createNotificationChannelsForAccount(account: AccountEntity, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelGroup = NotificationChannelGroup(account.identifier, account.fullName)
            notificationManager.createNotificationChannelGroup(channelGroup)
            val channels = TuskyNotificationChannel.values()
                .mapNotNull { it.makeNotificationChannel(context, account.identifier) }

            notificationManager.createNotificationChannels(channels)
        }
    }

    fun deleteNotificationChannelsForAccount(account: AccountEntity, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.deleteNotificationChannelGroup(account.identifier)
        }
    }

    fun areNotificationsEnabled(context: Context, accountManager: AccountManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // on Android >= O, notifications are enabled, if at least one channel is enabled
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.areNotificationsEnabled()) {
                for (channel in notificationManager.notificationChannels) {
                    if (channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE) {
                        Log.d(TAG, "NotificationsEnabled")
                        return true
                    }
                }
            }
            Log.d(TAG, "NotificationsDisabled")
            false
        } else {
            // on Android < O, notifications are enabled, if at least one account has notification enabled
            accountManager.areNotificationsEnabled()
        }
    }

    fun enablePullNotifications(context: Context?) {
        val workManager = WorkManager.getInstance(context!!)
        workManager.cancelAllWorkByTag(NOTIFICATION_PULL_TAG)

        // Periodic work requests are supposed to start running soon after being enqueued. In
        // practice that may not be soon enough, so create and enqueue an expedited one-time
        // request to get new notifications immediately.
        val fetchNotifications = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueue(fetchNotifications)
        val workRequest = PeriodicWorkRequest.Builder(
            NotificationWorker::class.java,
            PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
            PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
            TimeUnit.MILLISECONDS
        )
            .addTag(NOTIFICATION_PULL_TAG)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        workManager.enqueue(workRequest)
        Log.d(
            TAG,
            "enabled notification checks with " + PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS + "ms interval"
        )
    }

    fun disablePullNotifications(context: Context?) {
        WorkManager.getInstance(context!!).cancelAllWorkByTag(NOTIFICATION_PULL_TAG)
        Log.d(TAG, "disabled notification checks")
    }

    fun clearNotificationsForActiveAccount(context: Context, accountManager: AccountManager) {
        val (id) = accountManager.activeAccount ?: return
        val accountId = id.toInt()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (androidNotification in notificationManager.activeNotifications) {
            if (accountId == androidNotification.id) {
                notificationManager.cancel(androidNotification.tag, androidNotification.id)
            }
        }
    }

    fun filterNotification(
        notificationManager: NotificationManager,
        account: AccountEntity,
        notification: Notification
    ): Boolean {
        return filterNotification(notificationManager, account, notification.type)
    }

    fun filterNotification(
        notificationManager: NotificationManager,
        account: AccountEntity,
        type: Notification.Type
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getChannelId(account, type)
                ?: // unknown notificationtype
                return false
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE
        }
        return when (type) {
            Notification.Type.MENTION -> account.notificationsMentioned
            Notification.Type.STATUS -> account.notificationsSubscriptions
            Notification.Type.FOLLOW -> account.notificationsFollowed
            Notification.Type.FOLLOW_REQUEST -> account.notificationsFollowRequested
            Notification.Type.REBLOG -> account.notificationsReblogged
            Notification.Type.FAVOURITE -> account.notificationsFavorited
            Notification.Type.POLL -> account.notificationsPolls
            Notification.Type.SIGN_UP -> account.notificationsSignUps
            Notification.Type.UPDATE -> account.notificationsUpdates
            Notification.Type.REPORT -> account.notificationsReports
            else -> false
        }
    }

    private fun getChannelId(account: AccountEntity, notification: Notification): String? {
        return getChannelId(account, notification.type)
    }

    private fun getChannelId(account: AccountEntity, type: Notification.Type): String? {
        return TuskyNotificationChannel.from(type)?.channelId(account.identifier)
    }

    private fun setSoundVibrationLight(
        account: AccountEntity,
        builder: NotificationCompat.Builder
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return // do nothing on Android O or newer, the system uses the channel settings anyway
        }
        if (account.notificationSound) {
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
        }
        if (account.notificationVibration) {
            builder.setVibrate(longArrayOf(500, 500))
        }
        if (account.notificationLight) {
            builder.setLights(-0xd46f27, 300, 1000)
        }
    }

    private fun wrapItemAt(notification: StatusBarNotification): String {
        return notification.notification.extras.getString(EXTRA_ACCOUNT_NAME)!!
            .unicodeWrap() // getAccount().getName());
    }

    private fun joinNames(context: Context, notifications: List<StatusBarNotification>): String? {
        if (notifications.size > 3) {
            val length = notifications.size
            // notifications.get(0).getNotification().extras.getString(EXTRA_ACCOUNT_NAME);
            return String.format(
                context.getString(R.string.notification_summary_large),
                wrapItemAt(notifications[length - 1]),
                wrapItemAt(notifications[length - 2]),
                wrapItemAt(notifications[length - 3]),
                length - 3
            )
        } else if (notifications.size == 3) {
            return String.format(
                context.getString(R.string.notification_summary_medium),
                wrapItemAt(notifications[2]),
                wrapItemAt(notifications[1]),
                wrapItemAt(notifications[0])
            )
        } else if (notifications.size == 2) {
            return String.format(
                context.getString(R.string.notification_summary_small),
                wrapItemAt(notifications[1]),
                wrapItemAt(notifications[0])
            )
        }
        return null
    }

    private fun titleForType(
        context: Context,
        notification: Notification,
        account: AccountEntity
    ): String? {
        val accountName = notification.account.name.unicodeWrap()
        return when (notification.type) {
            Notification.Type.MENTION -> String.format(
                context.getString(R.string.notification_mention_format),
                accountName
            )
            Notification.Type.STATUS -> String.format(
                context.getString(R.string.notification_subscription_format),
                accountName
            )
            Notification.Type.FOLLOW -> String.format(
                context.getString(R.string.notification_follow_format),
                accountName
            )
            Notification.Type.FOLLOW_REQUEST -> String.format(
                context.getString(R.string.notification_follow_request_format),
                accountName
            )
            Notification.Type.FAVOURITE -> String.format(
                context.getString(R.string.notification_favourite_format),
                accountName
            )
            Notification.Type.REBLOG -> String.format(
                context.getString(R.string.notification_reblog_format),
                accountName
            )
            Notification.Type.POLL -> if (notification.status!!.account.id == account.accountId) {
                context.getString(R.string.poll_ended_created)
            } else {
                context.getString(R.string.poll_ended_voted)
            }
            Notification.Type.SIGN_UP -> String.format(
                context.getString(R.string.notification_sign_up_format),
                accountName
            )
            Notification.Type.UPDATE -> String.format(
                context.getString(R.string.notification_update_format),
                accountName
            )
            Notification.Type.REPORT -> context.getString(
                R.string.notification_report_format,
                account.domain
            )
            else -> null
        }
    }

    private fun bodyForType(
        notification: Notification,
        context: Context,
        alwaysOpenSpoiler: Boolean
    ): String? {
        return when (notification.type) {
            Notification.Type.FOLLOW, Notification.Type.FOLLOW_REQUEST, Notification.Type.SIGN_UP -> "@" + notification.account.username
            Notification.Type.MENTION, Notification.Type.FAVOURITE, Notification.Type.REBLOG, Notification.Type.STATUS -> if (!TextUtils.isEmpty(
                    notification.status!!.spoilerText
                ) && !alwaysOpenSpoiler
            ) {
                notification.status.spoilerText
            } else {
                notification.status.content.parseAsMastodonHtml().toString()
            }
            Notification.Type.POLL -> return if (!TextUtils.isEmpty(
                    notification.status!!.spoilerText
                ) && !alwaysOpenSpoiler
            ) {
                notification.status.spoilerText
            } else {
                val builder = StringBuilder(notification.status.content.parseAsMastodonHtml())
                builder.append('\n')
                val poll = notification.status.poll
                val options = poll!!.options
                var i = 0
                while (i < options.size) {
                    val (title, votesCount) = options[i]
                    builder.append(
                        buildDescription(
                            title,
                            calculatePercent(votesCount, poll.votersCount, poll.votesCount),
                            poll.ownVotes != null && poll.ownVotes.contains(i),
                            context
                        )
                    )
                    builder.append('\n')
                    ++i
                }
                builder.toString()
            }
            Notification.Type.REPORT -> context.getString(
                R.string.notification_header_report_format,
                notification.account.name.unicodeWrap(),
                notification.report!!.targetAccount.name.unicodeWrap()
            )
            else -> null
        }
    }

    fun pendingIntentFlags(mutable: Boolean): Int {
        return if (mutable) {
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
