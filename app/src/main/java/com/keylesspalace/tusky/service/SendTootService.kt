package com.keylesspalace.tusky.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import android.support.v4.app.ServiceCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
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

    private var notificationId = -1 // use negative ids to not clash with other notis
    private val tootsToSend = mutableMapOf<Int, TootToSend>()
    private val timer = Timer()


    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        Log.d(TAG, "new Intent")

        val tootToSend = intent.getParcelableExtra<TootToSend>(KEY_TOOT)

        if(tootToSend == null) {
            throw IllegalStateException("SendTootService started without $KEY_TOOT extra")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "toot", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)

        }

        var notificationText = tootToSend.warningText
        if(notificationText.isBlank()) {
            notificationText = tootToSend.text
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Sending Toot...")
                .setContentText(notificationText)
                //.setContentIntent(resultPendingIntent)
                //.setDeleteIntent(deletePendingIntent)
                .setProgress(1, 0, true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(this, R.color.primary))


        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        startForeground(notificationId, builder.build())

        tootsToSend[notificationId] = tootToSend
        sendToot(notificationId)

        notificationId--


        return Service.START_STICKY

    }


    private fun sendToot(tootId: Int) {

        // when tootToSend == null, sending has been canceled
        val tootToSend = tootsToSend[tootId] ?: return

        Log.d("SendTootService", "sendingToot "+tootId+" retries: "+tootToSend.retries+ " thread "+ Thread.currentThread().name)

        // when account == null, user has logged out, cancle sending
        val account = accountManager.getAccountById(tootToSend.accountId)

        if(account == null) {
            tootsToSend.remove(tootId)
            return
        }

        tootToSend.retries++

        mastodonApi.createStatus(
                "Bearer " + account.accessToken,
                account.domain,
                tootToSend.text,
                tootToSend.inReplyToId,
                tootToSend.warningText,
                tootToSend.visibility,
                tootToSend.sensitive,
                tootToSend.mediaIds,
                tootToSend.idempotencyKey
        ).enqueue(object: Callback<Status> {
            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                if(response.isSuccessful) {
                    tootsToSend.remove(tootId)

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(tootId)

                    if(tootsToSend.isEmpty()) {
                        stopForeground(true)
                        stopSelf()
                    }

                } else {

                }
            }

            override fun onFailure(call: Call<Status>, t: Throwable) {

                var backoff = 1000L*tootToSend.retries
                if(backoff > MAX_RETRY_INTERVAL) {
                    backoff = MAX_RETRY_INTERVAL
                }

                timer.schedule(object : TimerTask() {
                    override fun run() {
                        sendToot(tootId)
                    }
                }, backoff)

            }

        })


    }

    companion object {
        private const val TAG = "SendTootService"

        private const val KEY_TOOT = "toot"
        private const val CHANNEL_ID = "send_toots"

        private const val MAX_RETRY_INTERVAL = 60*1000L // 1 minute


        @JvmStatic
        fun sendTootIntent(context: Context,
                           text: String,
                           warningText: String,
                           inReplyToId: String?,
                           visibility: Status.Visibility,
                           sensitive: Boolean,
                           mediaIds: List<String>,
                           account: AccountEntity
        ): Intent {
            val intent = Intent(context, SendTootService::class.java)

            val idempotencyKey = StringUtils.randomAlphanumericString(16)

            val tootToSend = TootToSend(text, warningText, inReplyToId, visibility.serverString(), sensitive, mediaIds, account.id, idempotencyKey, 0)

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
                      val idempotencyKey: String,
                      var retries: Int): Parcelable