package com.keylesspalace.tusky.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import android.support.v4.app.ServiceCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import com.keylesspalace.tusky.ComposeActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TuskyApplication
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.receiver.TimelineReceiver
import com.keylesspalace.tusky.util.SaveTootHelper
import com.keylesspalace.tusky.util.StringUtils
import dagger.android.AndroidInjection
import kotlinx.android.parcel.Parcelize
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*
import javax.inject.Inject

class SendTootService: Service(), Injectable {

    @Inject
    lateinit var mastodonApi: MastodonApi
    @Inject
    lateinit var accountManager: AccountManager

    private lateinit var saveTootHelper: SaveTootHelper

    private val tootsToSend = mutableMapOf<Int, TootToSend>()
    private val sendCalls = mutableMapOf<Int, Call<Status>>()


    private val timer = Timer()


    override fun onCreate() {
        AndroidInjection.inject(this)
        saveTootHelper = SaveTootHelper(TuskyApplication.getDB().tootDao(), this)
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        if(intent.hasExtra(KEY_TOOT)) {

            val tootToSend = intent.getParcelableExtra<TootToSend>(KEY_TOOT)

            if (tootToSend == null) {
                throw IllegalStateException("SendTootService started without $KEY_TOOT extra")
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, getString(R.string.send_toot_notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)

            }

            var notificationText = tootToSend.warningText
            if (notificationText.isBlank()) {
                notificationText = tootToSend.text
            }

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(getString(R.string.send_toot_notification_title))
                    .setContentText(notificationText)
                    .setProgress(1, 0, true)
                    .setOngoing(true)
                    .setColor(ContextCompat.getColor(this, R.color.primary))
                    .addAction(0, getString(android.R.string.cancel), cancelSendingIntent(notificationId))

            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            startForeground(notificationId, builder.build())

            tootsToSend[notificationId] = tootToSend
            sendToot(notificationId)

            notificationId--

            return Service.START_STICKY

        } else {

            if(intent.hasExtra(KEY_CANCEL)) {
                cancelSending(intent.getIntExtra(KEY_CANCEL, 0))
            } else {

            }

            return Service.START_NOT_STICKY
        }

    }

    private fun sendToot(tootId: Int) {

        // when tootToSend == null, sending has been canceled
        val tootToSend = tootsToSend[tootId] ?: return

        // when account == null, user has logged out, cancel sending
        val account = accountManager.getAccountById(tootToSend.accountId)

        if(account == null) {
            tootsToSend.remove(tootId)
            return
        }

        tootToSend.retries++

        val sendCall = mastodonApi.createStatus(
                "Bearer " + account.accessToken,
                account.domain,
                tootToSend.text,
                tootToSend.inReplyToId,
                tootToSend.warningText,
                tootToSend.visibility,
                tootToSend.sensitive,
                tootToSend.mediaIds,
                tootToSend.idempotencyKey
        )


        sendCalls[tootId] = sendCall

        val callback = object: Callback<Status> {
            override fun onResponse(call: Call<Status>, response: Response<Status>) {

                tootsToSend.remove(tootId)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (response.isSuccessful) {

                    val intent = Intent(TimelineReceiver.Types.STATUS_COMPOSED)
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

                    // If the status was loaded from a draft, delete the draft and associated media files.
                    // TODO

                    if (tootsToSend.isEmpty()) {
                        ServiceCompat.stopForeground(this@SendTootService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }

                    notificationManager.cancel(tootId)


                } else {
                    // the server refused to accept the toot, save toot & show error message
                    saveTootToDrafts(tootToSend)

                    val builder = NotificationCompat.Builder(this@SendTootService, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notify)
                            .setContentTitle(getString(R.string.send_toot_notification_error_title))
                            .setContentText(getString(R.string.send_toot_notification_error_content))
                            .setColor(ContextCompat.getColor(this@SendTootService, R.color.primary))

                    notificationManager.notify(tootId, builder.build())

                    if (tootsToSend.isEmpty()) {
                        ServiceCompat.stopForeground(this@SendTootService, ServiceCompat.STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }

                }
            }

            override fun onFailure(call: Call<Status>, t: Throwable) {
                var backoff = 1000L*tootToSend.retries
                if (backoff > MAX_RETRY_INTERVAL) {
                    backoff = MAX_RETRY_INTERVAL
                }

                timer.schedule(object : TimerTask() {
                    override fun run() {
                        sendToot(tootId)
                    }
                }, backoff)
            }
        }

        sendCall.enqueue(callback)

    }

    private fun cancelSending(tootId: Int) {
        val tootToCancel = tootsToSend.remove(tootId)
        if(tootToCancel != null) {
            val sendCall = sendCalls.remove(tootId)
            sendCall?.cancel()

            saveTootToDrafts(tootToCancel)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val builder = NotificationCompat.Builder(this@SendTootService, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(getString(R.string.send_toot_notification_cancel_title))
                    .setContentText(getString(R.string.send_toot_notification_cancel_content))
                    .setColor(ContextCompat.getColor(this@SendTootService, R.color.primary))

            notificationManager.notify(tootId, builder.build())

            timer.schedule(object : TimerTask() {
                override fun run() {
                    notificationManager.cancel(tootId)
                }
            }, 5000)

            if (tootsToSend.isEmpty()) {
                ServiceCompat.stopForeground(this@SendTootService, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
            }

        }
    }

    private fun saveTootToDrafts(toot: TootToSend) {

        saveTootHelper.saveToot(toot.text,
                toot.warningText,
                null,
                listOf<ComposeActivity.QueuedMedia>(),
                toot.savedTootUid,
                toot.inReplyToId,
                null,
                null,
                Status.Visibility.byString(toot.visibility))
    }

    private fun cancelSendingIntent(tootId: Int): PendingIntent {

        val intent = Intent(this, SendTootService::class.java)

        intent.putExtra(KEY_CANCEL, tootId)

        return PendingIntent.getService(this, tootId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }


    companion object {

        private const val KEY_TOOT = "toot"
        private const val KEY_CANCEL = "cancel_id"
        private const val CHANNEL_ID = "send_toots"

        private const val MAX_RETRY_INTERVAL = 60*1000L // 1 minute

        private var notificationId = -1 // use negative ids to not clash with other notis

        @JvmStatic
        fun sendTootIntent(context: Context,
                           text: String,
                           warningText: String,
                           inReplyToId: String?,
                           visibility: Status.Visibility,
                           sensitive: Boolean,
                           mediaIds: List<String>,
                           account: AccountEntity,
                           savedTootUid: Int
        ): Intent {
            val intent = Intent(context, SendTootService::class.java)

            val idempotencyKey = StringUtils.randomAlphanumericString(16)

            val tootToSend = TootToSend(text, warningText, inReplyToId, visibility.serverString(), sensitive, mediaIds, account.id, savedTootUid, idempotencyKey, 0)

            intent.putExtra(KEY_TOOT, tootToSend)

            return intent
        }

    }
}

@Parcelize
data class TootToSend(val text: String,
                      val warningText: String,
                      val inReplyToId: String?,
                      val visibility: String,
                      val sensitive: Boolean,
                      val mediaIds: List<String>,
                      val accountId: Long,
                      val savedTootUid: Int,
                      val idempotencyKey: String,
                      var retries: Int): Parcelable