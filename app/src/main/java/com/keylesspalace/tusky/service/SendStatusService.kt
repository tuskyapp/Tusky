package com.keylesspalace.tusky.service

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusScheduledEvent
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.NewStatus
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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

    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    private val statusesToSend = ConcurrentHashMap<Int, StatusToSend>()
    private val sendCalls = ConcurrentHashMap<Int, Call<Status>>()

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        if (intent.hasExtra(KEY_STATUS)) {
            val statusToSend = intent.getParcelableExtra<StatusToSend>(KEY_STATUS)
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
                .setColor(ContextCompat.getColor(this, R.color.notification_color))
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

        val newStatus = NewStatus(
            statusToSend.text,
            statusToSend.warningText,
            statusToSend.inReplyToId,
            statusToSend.visibility,
            statusToSend.sensitive,
            statusToSend.mediaIds,
            statusToSend.scheduledAt,
            statusToSend.poll
        )

        val sendCall = mastodonApi.createStatus(
            "Bearer " + account.accessToken,
            account.domain,
            statusToSend.idempotencyKey,
            newStatus
        )

        sendCalls[statusId] = sendCall

        val callback = object : Callback<Status> {
            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                serviceScope.launch {

                    val scheduled = !statusToSend.scheduledAt.isNullOrEmpty()
                    statusesToSend.remove(statusId)

                    if (response.isSuccessful) {
                        // If the status was loaded from a draft, delete the draft and associated media files.
                        if (statusToSend.draftId != 0) {
                            draftHelper.deleteDraftAndAttachments(statusToSend.draftId)
                        }

                        if (scheduled) {
                            response.body()?.let(::StatusScheduledEvent)?.let(eventHub::dispatch)
                        } else {
                            response.body()?.let(::StatusComposedEvent)?.let(eventHub::dispatch)
                        }

                        notificationManager.cancel(statusId)
                    } else {
                        // the server refused to accept the status, save status & show error message
                        saveStatusToDrafts(statusToSend)

                        val builder = NotificationCompat.Builder(this@SendStatusService, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notify)
                            .setContentTitle(getString(R.string.send_post_notification_error_title))
                            .setContentText(getString(R.string.send_post_notification_saved_content))
                            .setColor(
                                ContextCompat.getColor(
                                    this@SendStatusService,
                                    R.color.notification_color
                                )
                            )

                        notificationManager.cancel(statusId)
                        notificationManager.notify(errorNotificationId--, builder.build())
                    }
                    stopSelfWhenDone()
                }
            }

            override fun onFailure(call: Call<Status>, t: Throwable) {
                serviceScope.launch {
                    var backoff = TimeUnit.SECONDS.toMillis(statusToSend.retries.toLong())
                    if (backoff > MAX_RETRY_INTERVAL) {
                        backoff = MAX_RETRY_INTERVAL
                    }

                    delay(backoff)
                    sendStatus(statusId)
                }
            }
        }

        sendCall.enqueue(callback)
    }

    private fun stopSelfWhenDone() {

        if (statusesToSend.isEmpty()) {
            ServiceCompat.stopForeground(this@SendStatusService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancelSending(statusId: Int) = serviceScope.launch {
        val statusToCancel = statusesToSend.remove(statusId)
        if (statusToCancel != null) {
            val sendCall = sendCalls.remove(statusId)
            sendCall?.cancel()

            saveStatusToDrafts(statusToCancel)

            val builder = NotificationCompat.Builder(this@SendStatusService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.send_post_notification_cancel_title))
                .setContentText(getString(R.string.send_post_notification_saved_content))
                .setColor(ContextCompat.getColor(this@SendStatusService, R.color.notification_color))

            notificationManager.notify(statusId, builder.build())

            delay(5000)
        }
    }

    private suspend fun saveStatusToDrafts(status: StatusToSend) {
        draftHelper.saveDraft(
            draftId = status.draftId,
            accountId = status.accountId,
            inReplyToId = status.inReplyToId,
            content = status.text,
            contentWarning = status.warningText,
            sensitive = status.sensitive,
            visibility = Status.Visibility.byString(status.visibility),
            mediaUris = status.mediaUris,
            mediaDescriptions = status.mediaDescriptions,
            poll = status.poll,
            failedToSend = true
        )
    }

    private fun cancelSendingIntent(statusId: Int): PendingIntent {
        val intent = Intent(this, SendStatusService::class.java)
        intent.putExtra(KEY_CANCEL, statusId)
        return PendingIntent.getService(this, statusId, intent, NotificationHelper.pendingIntentFlags(false))
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
    }

    companion object {

        private const val KEY_STATUS = "status"
        private const val KEY_CANCEL = "cancel_id"
        private const val CHANNEL_ID = "send_toots"

        private val MAX_RETRY_INTERVAL = TimeUnit.MINUTES.toMillis(1)

        private var sendingNotificationId = -1 // use negative ids to not clash with other notis
        private var errorNotificationId = Int.MIN_VALUE // use even more negative ids to not clash with other notis

        @JvmStatic
        fun sendStatusIntent(
            context: Context,
            statusToSend: StatusToSend
        ): Intent {
            val intent = Intent(context, SendStatusService::class.java)
            intent.putExtra(KEY_STATUS, statusToSend)

            if (statusToSend.mediaUris.isNotEmpty()) {
                // forward uri permissions
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uriClip = ClipData(
                    ClipDescription("Status Media", arrayOf("image/*", "video/*")),
                    ClipData.Item(statusToSend.mediaUris[0])
                )
                statusToSend.mediaUris
                    .drop(1)
                    .forEach { mediaUri ->
                        uriClip.addItem(ClipData.Item(mediaUri))
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
    val mediaIds: List<String>,
    val mediaUris: List<String>,
    val mediaDescriptions: List<String>,
    val scheduledAt: String?,
    val inReplyToId: String?,
    val poll: NewPoll?,
    val replyingStatusContent: String?,
    val replyingStatusAuthorUsername: String?,
    val accountId: Long,
    val draftId: Int,
    val idempotencyKey: String,
    var retries: Int
) : Parcelable
