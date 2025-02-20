package com.keylesspalace.tusky.components.systemnotifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.MainActivity.Companion.composeIntent
import com.keylesspalace.tusky.MainActivity.Companion.openNotificationIntent
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.ComposeOptions
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.ApplicationScope
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.NotificationSubscribeResult
import com.keylesspalace.tusky.entity.RelationshipSeveranceEvent
import com.keylesspalace.tusky.entity.visibleNotificationTypes
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.receiver.SendStatusBroadcastReceiver
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CryptoUtil
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.buildDescription
import com.keylesspalace.tusky.viewdata.calculatePercent
import com.keylesspalace.tusky.worker.NotificationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush
import retrofit2.HttpException

@Singleton
class NotificationService @Inject constructor(
    private val notificationManager: NotificationManager,
    private val accountManager: AccountManager,
    private val api: MastodonApi,
    private val preferences: SharedPreferences,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private var workManager: WorkManager = WorkManager.getInstance(context)

    private var notificationId: Int = NOTIFICATION_ID_PRUNE_CACHE + 1

    init {
        createWorkerNotificationChannel()
    }

    fun areNotificationsEnabledBySystem(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // on Android >= O, notifications are enabled, if at least one channel is enabled

            if (notificationManager.areNotificationsEnabled()) {
                for (channel in notificationManager.notificationChannels) {
                    if (channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE) {
                        Log.d(TAG, "Notifications enabled for app by the system.")
                        return true
                    }
                }
            }
            Log.d(TAG, "Notifications disabled for app by the system.")

            return false
        } else {
            // on Android < O, notifications are enabled, if at least one account has notification enabled
            return accountManager.areNotificationsEnabled()
        }
    }

    suspend fun setupNotifications(account: AccountEntity?) {
        resetPushWhenDistributorIsMissing()

        if (arePushNotificationsAvailable()) {
            setupPushNotifications(account)
        }

        // At least as a fallback and otherwise as main source when there are no push distributors installed:
        enablePullNotifications()
    }

    fun enablePullNotifications() {
        val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
            NotificationWorker::class.java,
            PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(NOTIFICATION_PULL_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)

        Log.d(TAG, "Enabled pull checks with ${PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / 60000} minutes interval.")
    }

    fun createNotificationChannelsForAccount(account: AccountEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            data class ChannelData(
                val id: String,
                @StringRes val name: Int,
                @StringRes val description: Int,
            )

            val channelData = arrayOf(
                ChannelData(
                    getChannelId(account, Notification.Type.Follow)!!,
                    R.string.notification_follow_name,
                    R.string.notification_follow_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Mention)!!,
                    R.string.notification_mention_name,
                    R.string.notification_mention_descriptions,
                ),

                ChannelData(
                    getChannelId(account, Notification.Type.FollowRequest)!!,
                    R.string.notification_follow_request_name,
                    R.string.notification_follow_request_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Reblog)!!,
                    R.string.notification_boost_name,
                    R.string.notification_boost_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Favourite)!!,
                    R.string.notification_favourite_name,
                    R.string.notification_favourite_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Poll)!!,
                    R.string.notification_poll_name,
                    R.string.notification_poll_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Status)!!,
                    R.string.notification_subscription_name,
                    R.string.notification_subscription_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.SignUp)!!,
                    R.string.notification_sign_up_name,
                    R.string.notification_sign_up_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Update)!!,
                    R.string.notification_update_name,
                    R.string.notification_update_description,
                ),
                ChannelData(
                    getChannelId(account, Notification.Type.Report)!!,
                    R.string.notification_report_name,
                    R.string.notification_report_description,
                ),
            )
            // TODO enumerate all keys of Notification.Type and check if one is missing here?

            val channelGroup = NotificationChannelGroup(account.identifier, account.fullName)
            notificationManager.createNotificationChannelGroup(channelGroup)

            val channels = channelData.map {
                NotificationChannel(it.id, context.getString(it.name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(it.description)
                    enableLights(true)
                    lightColor = -0xd46f27
                    enableVibration(true)
                    setShowBadge(true)
                    group = account.identifier
                }
            }

            notificationManager.createNotificationChannels(channels)
        }
    }

    private fun deleteNotificationChannelsForAccount(account: AccountEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannelGroup(account.identifier)
        }
    }

    private fun enqueueOneTimeWorker(account: AccountEntity?) {
        val oneTimeRequestBuilder = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

        account?.let {
            val data = Data.Builder()
            data.putLong(NotificationWorker.KEY_ACCOUNT_ID, account.id)
            oneTimeRequestBuilder.setInputData(data.build())
        }

        workManager.enqueue(oneTimeRequestBuilder.build())
    }

    fun disablePullNotifications() {
        workManager.cancelUniqueWork(NOTIFICATION_PULL_NAME)
        Log.d(TAG, "Disabled pull checks.")
    }

    fun clearNotificationsForAccount(account: AccountEntity) {
        for (androidNotification in notificationManager.activeNotifications) {
            if (account.id.toInt() == androidNotification.id) {
                notificationManager.cancel(androidNotification.tag, androidNotification.id)
            }
        }
    }

    fun filterNotification(account: AccountEntity, type: Notification.Type): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getChannelId(account, type)
                ?: // unknown notificationtype
                return false
            val channel = notificationManager.getNotificationChannel(channelId)

            return channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE
        }

        return when (type) {
            Notification.Type.Mention -> account.notificationsMentioned
            Notification.Type.Status -> account.notificationsSubscriptions
            Notification.Type.Follow -> account.notificationsFollowed
            Notification.Type.FollowRequest -> account.notificationsFollowRequested
            Notification.Type.Reblog -> account.notificationsReblogged
            Notification.Type.Favourite -> account.notificationsFavorited
            Notification.Type.Poll -> account.notificationsPolls
            Notification.Type.SignUp -> account.notificationsSignUps
            Notification.Type.Update -> account.notificationsUpdates
            Notification.Type.Report -> account.notificationsReports
            Notification.Type.SeveredRelationship -> account.notificationsRelationshipSeveranceEvents
            Notification.Type.ModerationWarning -> account.notificationsModerationWarnings
            else -> false
        }
    }

    fun show(account: AccountEntity, notifications: List<Notification>) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (notifications.isEmpty()) {
            return
        }

        val newNotifications = ArrayList<NotificationWithIdAndTag>()

        val notificationsByType: Map<Notification.Type, List<Notification>> = notifications.groupBy { it.type }
        for ((type, notificationsForOneType) in notificationsByType) {
            val summary = createSummaryNotification(account, type, notificationsForOneType) ?: continue

            // NOTE Enqueue the summary first: Needed to avoid rate limit problems:
            //   ie. single notification is enqueued but later the summary one is filtered and thus no grouping
            //   takes place.
            newNotifications.add(summary)

            for (notification in notificationsForOneType) {
                val single = createNotification(notification, account) ?: continue
                newNotifications.add(single)
            }
        }

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        // NOTE having multiple summary notifications: this here should still collapse them in only one occurrence
        notificationManagerCompat.notify(newNotifications)
    }

    private fun createNotification(apiNotification: Notification, account: AccountEntity): NotificationWithIdAndTag? {
        val baseNotification = createBaseNotification(apiNotification, account) ?: return null

        return NotificationWithIdAndTag(
            apiNotification.id,
            account.id.toInt(),
            baseNotification
        )
    }

    @VisibleForTesting
    fun createBaseNotification(apiNotification: Notification, account: AccountEntity): android.app.Notification? {
        val channelId = getChannelId(account, apiNotification.type) ?: return null

        val body = apiNotification.rewriteToStatusTypeIfNeeded(account.accountId)

        // Check for an existing notification matching this account and api notification
        var existingAndroidNotification: android.app.Notification? = null
        val activeNotifications = notificationManager.activeNotifications
        for (androidNotification in activeNotifications) {
            if (body.id == androidNotification.tag && account.id.toInt() == androidNotification.id) {
                existingAndroidNotification = androidNotification.notification
            }
        }

        notificationId++

        val builder = if (existingAndroidNotification == null) {
            getNotificationBuilder(body, account, channelId)
        } else {
            NotificationCompat.Builder(context, existingAndroidNotification)
        }

        val t = titleForType(body, account)
        val b = bodyForType(body, account)

        Log.d(TAG, "titleForType: $t")
        Log.d(TAG, "bodyForType: $b")

        builder
            .setContentTitle(t)
            .setContentText(b)

        if (body.type == Notification.Type.Mention || body.type == Notification.Type.Poll) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bodyForType(body, account))
            )
        }

        if (body.type != Notification.Type.SeveredRelationship && body.type != Notification.Type.ModerationWarning) {
            val accountAvatar = try {
                Glide.with(context)
                    .asBitmap()
                    .load(body.account.avatar)
                    .transform(RoundedCorners(20))
                    .submit()
                    .get()
            } catch (e: ExecutionException) {
                Log.d(TAG, "Error loading account avatar", e)
                BitmapFactory.decodeResource(context.resources, R.drawable.avatar_default)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Error loading account avatar", e)
                BitmapFactory.decodeResource(context.resources, R.drawable.avatar_default)
            }

            builder.setLargeIcon(accountAvatar)
        }

        // Reply to mention action; RemoteInput is available from KitKat Watch, but buttons are available from Nougat
        if (body.type == Notification.Type.Mention) {
            val replyRemoteInput = RemoteInput.Builder(KEY_REPLY)
                .setLabel(context.getString(R.string.label_quick_reply))
                .build()

            val quickReplyPendingIntent = getStatusReplyIntent(body, account, notificationId)

            val quickReplyAction =
                NotificationCompat.Action.Builder(
                    R.drawable.ic_reply_24dp,
                    context.getString(R.string.action_quick_reply),
                    quickReplyPendingIntent
                )
                    .addRemoteInput(replyRemoteInput)
                    .build()

            builder.addAction(quickReplyAction)

            val composeIntent = getStatusComposeIntent(body, account, notificationId)

            val composeAction =
                NotificationCompat.Action.Builder(
                    R.drawable.ic_reply_24dp,
                    context.getString(R.string.action_compose_shortcut),
                    composeIntent
                )
                    .setShowsUserInterface(true)
                    .build()

            builder.addAction(composeAction)
        }

        builder.addExtras(
            Bundle().apply {
                // Add the sending account's name, so it can be used also later when summarising this notification
                putString(EXTRA_ACCOUNT_NAME, body.account.name)
                putString(EXTRA_NOTIFICATION_TYPE, body.type.name)
            }
        )

        return builder.build()
    }

    /**
     * Create a notification that summarises the other notifications in this group.
     *
     * NOTE: We always create a summary notification (even for only one notification of that type):
     *   - No need to especially track the grouping
     *   - No need to change an existing single notification when there arrives another one of its group
     *   - Only the summary one will get announced
     */
    private fun createSummaryNotification(account: AccountEntity, type: Notification.Type, additionalNotifications: List<Notification>): NotificationWithIdAndTag? {
        val typeChannelId = getChannelId(account, type) ?: return null

        val summaryStackBuilder = TaskStackBuilder.create(context)
        summaryStackBuilder.addParentStack(MainActivity::class.java)
        val summaryResultIntent = openNotificationIntent(context, account.id, type)
        summaryStackBuilder.addNextIntent(summaryResultIntent)

        val summaryResultPendingIntent = summaryStackBuilder.getPendingIntent(
            (notificationId + account.id * 10000).toInt(),
            pendingIntentFlags(false)
        )

        val activeNotifications = getActiveNotifications(account.id, typeChannelId)

        val notificationCount = activeNotifications.size + additionalNotifications.size

        val title = context.resources.getQuantityString(R.plurals.notification_title_summary, notificationCount, notificationCount)
        val text = joinNames(activeNotifications, additionalNotifications)

        val summaryBuilder = NotificationCompat.Builder(context, typeChannelId)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(summaryResultPendingIntent)
            .setColor(context.getColor(R.color.notification_color))
            .setAutoCancel(true)
            .setContentTitle(title)
            .setContentText(text)
            .setShortcutId(account.id.toString())
            .setSubText(account.fullName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(typeChannelId)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        setSoundVibrationLight(account, summaryBuilder)

        val summaryTag = "$GROUP_SUMMARY_TAG.$typeChannelId"

        return NotificationWithIdAndTag(summaryTag, account.id.toInt(), summaryBuilder.build())
    }

    fun createWorkerNotification(@StringRes titleResource: Int): android.app.Notification {
        val title = context.getString(titleResource)
        return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_TASKS)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.ic_notify)
            .setOngoing(true)
            .build()
    }

    private fun getChannelId(account: AccountEntity, type: Notification.Type): String? {
        return when (type) {
            Notification.Type.Mention -> CHANNEL_MENTION + account.identifier
            Notification.Type.Status -> "CHANNEL_SUBSCRIPTIONS" + account.identifier
            Notification.Type.Follow -> "CHANNEL_FOLLOW" + account.identifier
            Notification.Type.FollowRequest -> "CHANNEL_FOLLOW_REQUEST" + account.identifier
            Notification.Type.Reblog -> "CHANNEL_BOOST" + account.identifier
            Notification.Type.Favourite -> "CHANNEL_FAVOURITE" + account.identifier
            Notification.Type.Poll -> "CHANNEL_POLL" + account.identifier
            Notification.Type.SignUp -> "CHANNEL_SIGN_UP" + account.identifier
            Notification.Type.Update -> "CHANNEL_UPDATES" + account.identifier
            Notification.Type.Report -> "CHANNEL_REPORT" + account.identifier
            else -> null
        }
    }

    /**
     * Return all active notifications, ignoring notifications that:
     * - belong to a different account
     * - belong to a different type
     * - are summary notifications
     */
    private fun getActiveNotifications(accountId: Long, typeChannelId: String): List<StatusBarNotification> {
        return notificationManager.activeNotifications.filter {
            val channelId = it.notification.group
            it.id == accountId.toInt() && channelId == typeChannelId && it.tag != "$GROUP_SUMMARY_TAG.$channelId"
        }
    }

    private fun getNotificationBuilder(notification: Notification, account: AccountEntity, channelId: String): NotificationCompat.Builder {
        val notificationType = notification.type
        val eventResultPendingIntent = if (notificationType == Notification.Type.ModerationWarning) {
            val warning = notification.moderationWarning!!
            val intent = Intent(Intent.ACTION_VIEW, "https://${account.domain}/disputes/strikes/${warning.id}".toUri())
            PendingIntent.getActivity(context, account.id.toInt(), intent, pendingIntentFlags(false))
        } else {
            val eventResultIntent = openNotificationIntent(context, account.id, notificationType)

            val eventStackBuilder = TaskStackBuilder.create(context)
            eventStackBuilder.addParentStack(MainActivity::class.java)
            eventStackBuilder.addNextIntent(eventResultIntent)

            eventStackBuilder.getPendingIntent(
                account.id.toInt(),
                pendingIntentFlags(false)
            )
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(eventResultPendingIntent)
            .setColor(context.getColor(R.color.notification_color))
            .setAutoCancel(true)
            .setShortcutId(account.id.toString())
            .setSubText(account.fullName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOnlyAlertOnce(true)
            .setGroup(channelId)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY) // Only ever alert for the summary notification

        setSoundVibrationLight(account, builder)

        return builder
    }

    private fun titleForType(notification: Notification, account: AccountEntity): String? {
        val accountName = notification.account.name.unicodeWrap()
        when (notification.type) {
            Notification.Type.Mention -> return context.getString(R.string.notification_mention_format, accountName)
            Notification.Type.Status -> return context.getString(R.string.notification_subscription_format, accountName)
            Notification.Type.Follow -> return context.getString(R.string.notification_follow_format, accountName)
            Notification.Type.FollowRequest -> return context.getString(R.string.notification_follow_request_format, accountName)
            Notification.Type.Favourite -> return context.getString(R.string.notification_favourite_format, accountName)
            Notification.Type.Reblog -> return context.getString(R.string.notification_reblog_format, accountName)
            Notification.Type.Poll -> return if (notification.status!!.account.id == account.accountId) {
                context.getString(R.string.poll_ended_created)
            } else {
                context.getString(R.string.poll_ended_voted)
            }
            Notification.Type.SignUp -> return context.getString(R.string.notification_sign_up_format, accountName)
            Notification.Type.Update -> return context.getString(R.string.notification_update_format, accountName)
            Notification.Type.Report -> return context.getString(R.string.notification_report_format, account.domain)
            Notification.Type.SeveredRelationship -> return context.getString(R.string.relationship_severance_event_title)
            Notification.Type.ModerationWarning -> return context.getString(R.string.moderation_warning)
            is Notification.Type.Unknown -> return null
        }
    }

    private fun bodyForType(notification: Notification, account: AccountEntity): String? {
        val alwaysOpenSpoiler = account.alwaysOpenSpoiler

        when (notification.type) {
            Notification.Type.Follow, Notification.Type.FollowRequest, Notification.Type.SignUp -> return "@" + notification.account.username
            Notification.Type.Mention, Notification.Type.Favourite, Notification.Type.Reblog, Notification.Type.Status -> return if (!notification.status?.spoilerText.isNullOrEmpty() && !alwaysOpenSpoiler) {
                notification.status.spoilerText
            } else {
                notification.status?.content?.parseAsMastodonHtml()?.toString()
            }
            Notification.Type.Poll -> if (!notification.status?.spoilerText.isNullOrEmpty() && !alwaysOpenSpoiler) {
                return notification.status.spoilerText
            } else {
                val poll = notification.status?.poll ?: return null

                val builder = StringBuilder(notification.status.content.parseAsMastodonHtml())
                builder.append('\n')

                poll.options.forEachIndexed { i, option ->
                    builder.append(
                        buildDescription(
                            option.title,
                            calculatePercent(option.votesCount, poll.votersCount, poll.votesCount),
                            poll.ownVotes.contains(i),
                            context
                        )
                    )
                    builder.append('\n')
                }

                return builder.toString()
            }
            Notification.Type.Report -> return context.getString(
                R.string.notification_header_report_format,
                notification.account.name.unicodeWrap(),
                notification.report!!.targetAccount.name.unicodeWrap()
            )
            Notification.Type.SeveredRelationship -> return severedRelationShipText(context, notification.event!!, account.domain)
            Notification.Type.ModerationWarning -> return context.getString(notification.moderationWarning!!.action.text)
            else -> return null
        }
    }

    private fun createWorkerNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_BACKGROUND_TASKS,
            context.getString(R.string.notification_listenable_worker_name),
            NotificationManager.IMPORTANCE_NONE
        )

        channel.description = context.getString(R.string.notification_listenable_worker_description)
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setShowBadge(false)

        notificationManager.createNotificationChannel(channel)
    }

    private fun setSoundVibrationLight(account: AccountEntity, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return // Do nothing on Android O or newer, the system uses only the channel settings
        }

        builder.setDefaults(0)

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

    private fun joinNames(notifications1: List<StatusBarNotification>, notifications2: List<Notification>): String? {
        val names = java.util.ArrayList<String>(notifications1.size + notifications2.size)

        for (notification in notifications1) {
            val author = notification.notification.extras.getString(EXTRA_ACCOUNT_NAME) ?: continue
            names.add(author)
        }

        for (noti in notifications2) {
            names.add(noti.account.name)
        }

        if (names.size > 3) {
            val length = names.size
            return context.getString(
                R.string.notification_summary_large,
                names[length - 1].unicodeWrap(),
                names[length - 2].unicodeWrap(),
                names[length - 3].unicodeWrap(),
                length - 3
            )
        } else if (names.size == 3) {
            return context.getString(
                R.string.notification_summary_medium,
                names[2].unicodeWrap(),
                names[1].unicodeWrap(),
                names[0].unicodeWrap()
            )
        } else if (names.size == 2) {
            return context.getString(
                R.string.notification_summary_small,
                names[1].unicodeWrap(),
                names[0].unicodeWrap()
            )
        }

        return null
    }

    private fun getStatusReplyIntent(apiNotification: Notification, account: AccountEntity, requestCode: Int): PendingIntent {
        val status = checkNotNull(apiNotification.status)

        val inReplyToId = status.id
        val actionableStatus = status.actionableStatus
        val replyVisibility = actionableStatus.visibility
        val contentWarning = actionableStatus.spoilerText
        val mentions = actionableStatus.mentions

        val mentionedUsernames = buildSet {
            add(actionableStatus.account.username)
            for (mention in mentions) {
                add(mention.username)
            }
            remove(account.username)
        }

        val replyIntent = Intent(context, SendStatusBroadcastReceiver::class.java)
            .setAction(REPLY_ACTION)
            .putExtra(KEY_SENDER_ACCOUNT_ID, account.id)
            .putExtra(KEY_SENDER_ACCOUNT_IDENTIFIER, account.identifier)
            .putExtra(KEY_SENDER_ACCOUNT_FULL_NAME, account.fullName)
            .putExtra(KEY_SERVER_NOTIFICATION_ID, apiNotification.id)
            .putExtra(KEY_CITED_STATUS_ID, inReplyToId)
            .putExtra(KEY_VISIBILITY, replyVisibility)
            .putExtra(KEY_SPOILER, contentWarning)
            .putExtra(KEY_MENTIONS, mentionedUsernames.toTypedArray<String?>())

        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            replyIntent,
            pendingIntentFlags(true)
        )
    }

    private fun getStatusComposeIntent(apiNotification: Notification, account: AccountEntity, requestCode: Int): PendingIntent {
        val status = checkNotNull(apiNotification.status)

        val citedLocalAuthor = status.account.localUsername
        val citedText = status.content.parseAsMastodonHtml().toString()
        val inReplyToId = status.id
        val actionableStatus = status.actionableStatus
        val replyVisibility = actionableStatus.visibility
        val contentWarning = actionableStatus.spoilerText
        val mentions = actionableStatus.mentions

        val mentionedUsernames = buildSet {
            add(actionableStatus.account.username)
            for (mention in mentions) {
                add(mention.username)
            }
            remove(account.username)
        }

        val composeOptions = ComposeOptions()
        composeOptions.inReplyToId = inReplyToId
        composeOptions.replyVisibility = replyVisibility
        composeOptions.contentWarning = contentWarning
        composeOptions.replyingStatusAuthor = citedLocalAuthor
        composeOptions.replyingStatusContent = citedText
        composeOptions.mentionedUsernames = mentionedUsernames
        composeOptions.modifiedInitialState = true
        composeOptions.language = actionableStatus.language
        composeOptions.kind = ComposeActivity.ComposeKind.NEW

        val composeIntent = composeIntent(context, composeOptions, account.id, apiNotification.id, account.id.toInt())

        // make sure a new instance of MainActivity is started and old ones get destroyed
        composeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        return PendingIntent.getActivity(
            context.applicationContext,
            requestCode,
            composeIntent,
            pendingIntentFlags(false)
        )
    }

    private fun pendingIntentFlags(mutable: Boolean): Int {
        return if (mutable) {
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
    }

    suspend fun disableAllNotifications() {
        disablePushNotificationsForAllAccounts()
        disablePullNotifications()
    }

    suspend fun disableNotificationsForAccount(account: AccountEntity) {
        disablePushNotificationsForAccount(account)

        deleteNotificationChannelsForAccount(account)

        if (!areNotificationsEnabledBySystem()) {
            // TODO this is sort of a hack, it means: are there now no active accounts?

            disablePullNotifications()
        }
    }

    //
    // Push notification section
    //

    fun arePushNotificationsAvailable(): Boolean =
        UnifiedPush.getDistributors(context).isNotEmpty()

    private suspend fun setupPushNotifications(account: AccountEntity?) {
        val relevantAccounts: List<AccountEntity> = if (account != null) {
            listOf(account)
        } else {
            accountManager.accounts
        }

        relevantAccounts.forEach {
            val notificationGroupEnabled = Build.VERSION.SDK_INT < 28 ||
                notificationManager.getNotificationChannelGroup(it.identifier)?.isBlocked == false
            val shouldEnable = it.notificationsEnabled && notificationGroupEnabled

            if (shouldEnable) {
                setupPushNotificationsForAccount(it)
                Log.d(TAG, "Enabled push notifications for account ${it.id}.")
            } else {
                disablePushNotificationsForAccount(it)
                Log.d(TAG, "Disabled push notifications for account ${it.id}.")
            }
        }
    }

    private suspend fun setupPushNotificationsForAccount(account: AccountEntity) {
        val currentSubscription = getActiveSubscription(account)

        if (currentSubscription != null) {
            val alertData = buildAlertsMap(account)

            if (alertData != currentSubscription.alerts) {
                // Update the subscription to match notification settings
                updatePushSubscription(account)
            } else {
                Log.d(TAG, "Nothing to be done. Current push subscription matches for account ${account.id}.")
            }
        } else {
            Log.d(TAG, "Trying to create a UnifiedPush subscription for account ${account.id}")

            // When changing the local UP distributor this is necessary first to enable the following callbacks (i. e. onNewEndpoint);
            //   make sure this is done in any inconsistent case (is not too often and doesn't hurt).
            unregisterPushEndpoint(account)

            UnifiedPush.registerAppWithDialog(context, account.id.toString(), features = arrayListOf(UnifiedPush.FEATURE_BYTES_MESSAGE))
            // Will lead to call of registerPushEndpoint()
        }
    }

    private fun resetPushWhenDistributorIsMissing() {
        val lastUsedPushProvider = preferences.getString(PrefKeys.LAST_USED_PUSH_PROVDER, null)
        // NOTE UnifiedPush.getSavedDistributor() cannot be used here as that is already null here if the
        //   distributor was uninstalled.

        if (lastUsedPushProvider.isNullOrEmpty() || UnifiedPush.getDistributors(context).contains(lastUsedPushProvider)) {
            return
        }

        Log.w(TAG, "Previous push provider ($lastUsedPushProvider) uninstalled. Resetting all accounts.")

        val editor = preferences.edit()
        editor.remove(PrefKeys.LAST_USED_PUSH_PROVDER)
        editor.apply()

        applicationScope.launch {
            accountManager.accounts.forEach {
                // reset all accounts, also does resetPushSettingsInAccount()
                unregisterPushEndpoint(it)
            }
        }
    }

    private suspend fun getActiveSubscription(account: AccountEntity): NotificationSubscribeResult? {
        api.pushNotificationSubscription(
            "Bearer ${account.accessToken}",
            account.domain
        ).fold(
            onSuccess = {
                if (!account.matchesPushSubscription(it.endpoint)) {
                    Log.w(TAG, "Server push endpoint does not match previously registered one: ${it.endpoint} vs. ${account.unifiedPushUrl}")

                    return null
                }

                return it
            },
            onFailure = { throwable ->
                if (throwable is HttpException && throwable.code() == 404) {
                    // this is alright; there is no subscription on the server
                    return null
                }

                Log.e(TAG, "Cannot get push subscription for account " + account.id + ": " + throwable.message, throwable)
                return null
            }
        )
    }

    private suspend fun disablePushNotificationsForAllAccounts() {
        accountManager.accounts.forEach {
            disablePushNotificationsForAccount(it)
        }
    }

    private suspend fun disablePushNotificationsForAccount(account: AccountEntity) {
        if (!account.isPushNotificationsEnabled()) {
            return
        }

        unregisterPushEndpoint(account)

        // this probably does nothing (distributor to handle this is missing)
        UnifiedPush.unregisterApp(context, account.id.toString())
    }

    fun fetchNotificationsOnPushMessage(account: AccountEntity) {
        // TODO should there be a rate limit here? Ie. we could be silent (can we?) for another notification in a short timeframe.

        Log.d(TAG, "Fetching notifications because of push for account ${account.id}")

        enqueueOneTimeWorker(account)
    }

    private fun buildAlertsMap(account: AccountEntity): Map<String, Boolean> =
        buildMap {
            visibleNotificationTypes.forEach {
                put(it.name, filterNotification(account, it))
            }
        }

    private fun buildAlertSubscriptionData(account: AccountEntity): Map<String, Boolean> =
        buildAlertsMap(account).mapKeys { "data[alerts][${it.key}]" }

    // Called by UnifiedPush callback in UnifiedPushBroadcastReceiver
    suspend fun registerPushEndpoint(
        account: AccountEntity,
        endpoint: String
    ) = withContext(Dispatchers.IO) {
        // Generate a prime256v1 key pair for WebPush
        // Decryption is unimplemented for now, since Mastodon uses an old WebPush
        // standard which does not send needed information for decryption in the payload
        // This makes it not directly compatible with UnifiedPush
        // As of now, we use it purely as a way to trigger a pull
        val keyPair = CryptoUtil.generateECKeyPair(CryptoUtil.CURVE_PRIME256_V1)
        val auth = CryptoUtil.secureRandomBytesEncoded(16)

        api.subscribePushNotifications(
            "Bearer ${account.accessToken}",
            account.domain,
            endpoint,
            keyPair.pubkey,
            auth,
            buildAlertSubscriptionData(account)
        ).onFailure { throwable ->
            Log.w(TAG, "Error setting push endpoint for account ${account.id}", throwable)
            disablePushNotificationsForAccount(account)
        }.onSuccess {
            Log.d(TAG, "UnifiedPush registration succeeded for account ${account.id}")

            accountManager.updateAccount(account) {
                copy(
                    pushPubKey = keyPair.pubkey,
                    pushPrivKey = keyPair.privKey,
                    pushAuth = auth,
                    pushServerKey = it.serverKey,
                    unifiedPushUrl = endpoint
                )
            }

            UnifiedPush.getAckDistributor(context)?.let {
                Log.d(TAG, "Saving distributor to preferences: $it")

                val editor = preferences.edit()
                editor.putString(PrefKeys.LAST_USED_PUSH_PROVDER, it)
                editor.apply()

                // TODO once this is selected it cannot be changed (except by wiping the application or uninstalling the provider)
            }
        }
    }

    // Synchronize the enabled / disabled state of notifications with server-side subscription (also NotificationBlockStateBroadcastReceiver).
    suspend fun updatePushSubscription(account: AccountEntity) {
        withContext(Dispatchers.IO) {
            api.updatePushNotificationSubscription(
                "Bearer ${account.accessToken}",
                account.domain,
                buildAlertSubscriptionData(account)
            ).onSuccess {
                Log.d(TAG, "UnifiedPush subscription updated for account ${account.id}")
                accountManager.updateAccount(account) {
                    copy(pushServerKey = it.serverKey)
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Could not update subscription ${throwable.message}")
            }
        }
    }

    suspend fun unregisterPushEndpoint(account: AccountEntity) {
        withContext(Dispatchers.IO) {
            api.unsubscribePushNotifications("Bearer ${account.accessToken}", account.domain)
                .onFailure { throwable ->
                    Log.w(TAG, "Error unregistering push endpoint for account " + account.id, throwable)
                }
                .onSuccess {
                    Log.d(TAG, "UnifiedPush unregistration succeeded for account " + account.id)
                    resetPushSettingsInAccount(account)
                }
        }
    }

    private suspend fun resetPushSettingsInAccount(account: AccountEntity) {
        accountManager.updateAccount(account) {
            copy(
                pushPubKey = "",
                pushPrivKey = "",
                pushAuth = "",
                pushServerKey = "",
                unifiedPushUrl = ""
            )
        }
    }

    companion object {
        const val TAG = "NotificationService"

        const val CHANNEL_MENTION: String = "CHANNEL_MENTION"
        const val KEY_CITED_STATUS_ID: String = "KEY_CITED_STATUS_ID"
        const val KEY_MENTIONS: String = "KEY_MENTIONS"
        const val KEY_REPLY: String = "KEY_REPLY"
        const val KEY_SENDER_ACCOUNT_FULL_NAME: String = "KEY_SENDER_ACCOUNT_FULL_NAME"
        const val KEY_SENDER_ACCOUNT_ID: String = "KEY_SENDER_ACCOUNT_ID"
        const val KEY_SENDER_ACCOUNT_IDENTIFIER: String = "KEY_SENDER_ACCOUNT_IDENTIFIER"
        const val KEY_SERVER_NOTIFICATION_ID: String = "KEY_SERVER_NOTIFICATION_ID"
        const val KEY_SPOILER: String = "KEY_SPOILER"
        const val KEY_VISIBILITY: String = "KEY_VISIBILITY"
        const val NOTIFICATION_ID_FETCH_NOTIFICATION: Int = 0
        const val NOTIFICATION_ID_PRUNE_CACHE: Int = 1
        const val REPLY_ACTION: String = "REPLY_ACTION"

        private const val CHANNEL_BACKGROUND_TASKS: String = "CHANNEL_BACKGROUND_TASKS"
        private const val EXTRA_ACCOUNT_NAME = BuildConfig.APPLICATION_ID + ".notification.extra.account_name"
        private const val EXTRA_NOTIFICATION_TYPE = BuildConfig.APPLICATION_ID + ".notification.extra.notification_type"
        private const val GROUP_SUMMARY_TAG = BuildConfig.APPLICATION_ID + ".notification.group_summary"
        private const val NOTIFICATION_PULL_NAME = "pullNotifications"

        private val numberFormat = NumberFormat.getNumberInstance()

        fun severedRelationShipText(
            context: Context,
            event: RelationshipSeveranceEvent,
            instanceName: String
        ): String {
            return when (event.type) {
                RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> {
                    val followers = numberFormat.format(event.followersCount)
                    val following = numberFormat.format(event.followingCount)
                    val followingText = context.resources.getQuantityString(R.plurals.accounts, event.followingCount, following)
                    context.getString(R.string.relationship_severance_event_domain_block, instanceName, event.targetName, followers, followingText)
                }

                RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> {
                    val followers = numberFormat.format(event.followersCount)
                    val following = numberFormat.format(event.followingCount)
                    val followingText = context.resources.getQuantityString(R.plurals.accounts, event.followingCount, following)
                    context.getString(R.string.relationship_severance_event_user_domain_block, event.targetName, followers, followingText)
                }

                RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> {
                    context.getString(R.string.relationship_severance_event_account_suspension, instanceName, event.targetName)
                }
            }
        }
    }
}
