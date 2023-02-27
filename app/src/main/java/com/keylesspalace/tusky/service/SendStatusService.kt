package com.keylesspalace.tusky.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusScheduledEvent
import com.keylesspalace.tusky.components.compose.MediaUploader
import com.keylesspalace.tusky.components.compose.UploadEvent
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.MediaAttribute
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.NewStatus
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.unsafeLazy
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SendStatusService : Service(), Injectable {

    @Inject
    lateinit var mastodonApi: MastodonApi
    @Inject
    lateinit var accountManager: AccountManager
    @Inject
    lateinit var eventHub: EventHub
    @Inject
    lateinit var draftHelper: DraftHelper
    @Inject
    lateinit var mediaUploader: MediaUploader

    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    private val statusesToSend = ConcurrentHashMap<Int, StatusToSend>()
    private val sendJobs = ConcurrentHashMap<Int, Job>()

    private val notificationManager by unsafeLazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra(KEY_STATUS)) {
            val statusToSend: StatusToSend = intent.getParcelableExtra(KEY_STATUS)
                ?: throw IllegalStateException("SendStatusService started without $KEY_STATUS extra")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, getString(R.string.send_post_notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }

            var notificationText = statusToSend.warningText
            if (notificationText.isBlank()) {
                notificationText = statusToSend.text
            }

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.send_post_notification_title))
                .setContentText(notificationText)
                .setProgress(1, 0, true)
                .setOngoing(true)
                .setColor(getColor(R.color.notification_color))
                .addAction(0, getString(android.R.string.cancel), cancelSendingIntent(sendingNotificationId))

            if (statusesToSend.size == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                startForeground(sendingNotificationId, builder.build())
            } else {
                notificationManager.notify(sendingNotificationId, builder.build())
            }

            statusesToSend[sendingNotificationId] = statusToSend
            sendStatus(sendingNotificationId--)
        } else {

            if (intent.hasExtra(KEY_CANCEL)) {
                cancelSending(intent.getIntExtra(KEY_CANCEL, 0))
            }
        }

        return START_NOT_STICKY
    }

    private fun sendStatus(statusId: Int) {

        // when statusToSend == null, sending has been canceled
        val statusToSend = statusesToSend[statusId] ?: return

        // when account == null, user has logged out, cancel sending
        val account = accountManager.getAccountById(statusToSend.accountId)

        if (account == null) {
            statusesToSend.remove(statusId)
            notificationManager.cancel(statusId)
            stopSelfWhenDone()
            return
        }

        statusToSend.retries++

        sendJobs[statusId] = serviceScope.launch {

            // first, wait for media uploads to finish
            val media = statusToSend.media.map { mediaItem ->
                if (mediaItem.id == null) {
                    when (val uploadState = mediaUploader.getMediaUploadState(mediaItem.localId)) {
                        is UploadEvent.FinishedEvent -> mediaItem.copy(id = uploadState.mediaId, processed = uploadState.processed)
                        is UploadEvent.ErrorEvent -> {
                            Log.w(TAG, "failed uploading media", uploadState.error)
                            failSending(statusId)
                            stopSelfWhenDone()
                            return@launch
                        }
                    }
                } else {
                    mediaItem
                }
            }

            // then wait until server finished processing the media
            try {
                var mediaCheckRetries = 0
                while (media.any { mediaItem -> !mediaItem.processed }) {
                    delay(1000L * mediaCheckRetries)
                    media.forEach { mediaItem ->
                        if (!mediaItem.processed) {
                            when (mastodonApi.getMedia(mediaItem.id!!).code()) {
                                200 -> mediaItem.processed = true // success
                                206 -> { } // media is still being processed, continue checking
                                else -> { // some kind of server error, retrying probably doesn't make sense
                                    failSending(statusId)
                                    stopSelfWhenDone()
                                    return@launch
                                }
                            }
                        }
                    }
                    mediaCheckRetries ++
                }
            } catch (e: Exception) {
                Log.w(TAG, "failed getting media status", e)
                retrySending(statusId)
                return@launch
            }

            // finally, send the new status
            val newStatus = NewStatus(
                status = statusToSend.text,
                warningText = statusToSend.warningText,
                inReplyToId = statusToSend.inReplyToId,
                visibility = statusToSend.visibility,
                sensitive = statusToSend.sensitive,
                mediaIds = media.map { it.id!! },
                scheduledAt = statusToSend.scheduledAt,
                poll = statusToSend.poll,
                language = statusToSend.language,
                mediaAttributes = media.map { media ->
                    MediaAttribute(
                        id = media.id!!,
                        description = media.description,
                        focus = media.focus?.toMastodonApiString(),
                        thumbnail = null,
                    )
                },
            )

            val sendResult = if (statusToSend.statusId == null) {
                mastodonApi.createStatus(
                    "Bearer " + account.accessToken,
                    account.domain,
                    statusToSend.idempotencyKey,
                    newStatus
                )
            } else {
                mastodonApi.editStatus(
                    statusToSend.statusId,
                    "Bearer " + account.accessToken,
                    account.domain,
                    statusToSend.idempotencyKey,
                    newStatus
                )
            }

            sendResult.fold({ sentStatus ->
                statusesToSend.remove(statusId)
                // If the status was loaded from a draft, delete the draft and associated media files.
                if (statusToSend.draftId != 0) {
                    draftHelper.deleteDraftAndAttachments(statusToSend.draftId)
                }

                mediaUploader.cancelUploadScope(*statusToSend.media.map { it.localId }.toIntArray())

                val scheduled = !statusToSend.scheduledAt.isNullOrEmpty()

                if (scheduled) {
                    eventHub.dispatch(StatusScheduledEvent(sentStatus))
                } else {
                    eventHub.dispatch(StatusComposedEvent(sentStatus))
                }

                notificationManager.cancel(statusId)
            }, { throwable ->
                Log.w(TAG, "failed sending status", throwable)
                if (throwable is HttpException) {
                    // the server refused to accept the status, save status & show error message
                    failSending(statusId)
                } else {
                    // a network problem occurred, let's retry sending the status
                    retrySending(statusId)
                }
            })
            stopSelfWhenDone()
        }
    }

    private suspend fun retrySending(statusId: Int) {
        // when statusToSend == null, sending has been canceled
        val statusToSend = statusesToSend[statusId] ?: return

        val backoff = TimeUnit.SECONDS.toMillis(statusToSend.retries.toLong()).coerceAtMost(MAX_RETRY_INTERVAL)

        delay(backoff)
        sendStatus(statusId)
    }

    private fun stopSelfWhenDone() {

        if (statusesToSend.isEmpty()) {
            ServiceCompat.stopForeground(this@SendStatusService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun failSending(statusId: Int) {
        val failedStatus = statusesToSend.remove(statusId)
        if (failedStatus != null) {

            mediaUploader.cancelUploadScope(*failedStatus.media.map { it.localId }.toIntArray())

            saveStatusToDrafts(failedStatus, failedToSendAlert = true)

            val notification = buildDraftNotification(
                R.string.send_post_notification_error_title,
                R.string.send_post_notification_saved_content,
                failedStatus.accountId,
                statusId
            )

            notificationManager.cancel(statusId)
            notificationManager.notify(errorNotificationId++, notification)
        }
    }

    private fun cancelSending(statusId: Int) = serviceScope.launch {
        val statusToCancel = statusesToSend.remove(statusId)
        if (statusToCancel != null) {

            mediaUploader.cancelUploadScope(*statusToCancel.media.map { it.localId }.toIntArray())

            val sendJob = sendJobs.remove(statusId)
            sendJob?.cancel()

            saveStatusToDrafts(statusToCancel, failedToSendAlert = false)

            val notification = buildDraftNotification(
                R.string.send_post_notification_cancel_title,
                R.string.send_post_notification_saved_content,
                statusToCancel.accountId,
                statusId
            )

            notificationManager.notify(statusId, notification)

            delay(5000)

            stopSelfWhenDone()
        }
    }

    private suspend fun saveStatusToDrafts(status: StatusToSend, failedToSendAlert: Boolean) {
        draftHelper.saveDraft(
            draftId = status.draftId,
            accountId = status.accountId,
            inReplyToId = status.inReplyToId,
            content = status.text,
            contentWarning = status.warningText,
            sensitive = status.sensitive,
            visibility = Status.Visibility.byString(status.visibility),
            mediaUris = status.media.map { it.uri },
            mediaDescriptions = status.media.map { it.description },
            mediaFocus = status.media.map { it.focus },
            poll = status.poll,
            failedToSend = true,
            failedToSendAlert = failedToSendAlert,
            scheduledAt = status.scheduledAt,
            language = status.language,
            statusId = status.statusId,
        )
    }

    private fun cancelSendingIntent(statusId: Int): PendingIntent {
        val intent = Intent(this, SendStatusService::class.java)
        intent.putExtra(KEY_CANCEL, statusId)
        return PendingIntent.getService(
            this,
            statusId,
            intent,
            NotificationHelper.pendingIntentFlags(false)
        )
    }

    private fun buildDraftNotification(
        @StringRes title: Int,
        @StringRes content: Int,
        accountId: Long,
        statusId: Int
    ): Notification {

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(NotificationHelper.ACCOUNT_ID, accountId)
        intent.putExtra(MainActivity.OPEN_DRAFTS, true)

        val pendingIntent = PendingIntent.getActivity(
            this,
            statusId,
            intent,
            NotificationHelper.pendingIntentFlags(false)
        )

        return NotificationCompat.Builder(this@SendStatusService, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(title))
            .setContentText(getString(content))
            .setColor(getColor(R.color.notification_color))
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
    }

    companion object {
        private const val TAG = "SendStatusService"

        private const val KEY_STATUS = "status"
        private const val KEY_CANCEL = "cancel_id"
        private const val CHANNEL_ID = "send_toots"

        private val MAX_RETRY_INTERVAL = TimeUnit.MINUTES.toMillis(1)

        private var sendingNotificationId = -1 // use negative ids to not clash with other notis
        private var errorNotificationId = Int.MIN_VALUE // use even more negative ids to not clash with other notis

        fun sendStatusIntent(
            context: Context,
            statusToSend: StatusToSend
        ): Intent {
            val intent = Intent(context, SendStatusService::class.java)
            intent.putExtra(KEY_STATUS, statusToSend)

            if (statusToSend.media.isNotEmpty()) {
                // forward uri permissions
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uriClip = ClipData(
                    ClipDescription("Status Media", arrayOf("image/*", "video/*")),
                    ClipData.Item(statusToSend.media[0].uri)
                )
                statusToSend.media
                    .drop(1)
                    .forEach { mediaItem ->
                        uriClip.addItem(ClipData.Item(mediaItem.uri))
                    }

                intent.clipData = uriClip
            }

            return intent
        }
    }
}

@Parcelize
data class StatusToSend(
    val text: String,
    val warningText: String,
    val visibility: String,
    val sensitive: Boolean,
    val media: List<MediaToSend>,
    val scheduledAt: String?,
    val inReplyToId: String?,
    val poll: NewPoll?,
    val replyingStatusContent: String?,
    val replyingStatusAuthorUsername: String?,
    val accountId: Long,
    val draftId: Int,
    val idempotencyKey: String,
    var retries: Int,
    val language: String?,
    val statusId: String?,
) : Parcelable

@Parcelize
data class MediaToSend(
    val localId: Int,
    val id: String?, // null if media is not yet completely uploaded
    val uri: String,
    val description: String?,
    val focus: Attachment.Focus?,
    var processed: Boolean
) : Parcelable
