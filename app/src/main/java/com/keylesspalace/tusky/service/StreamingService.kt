package com.keylesspalace.tusky.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.StreamEvent
import com.keylesspalace.tusky.network.MastodonApi
import dagger.android.AndroidInjection
import okhttp3.*
import javax.inject.Inject

class StreamingService: Service(), Injectable {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var gson: Gson

    private val sockets: MutableMap<Long, WebSocket> = mutableMapOf()

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    private fun stopStreamingForId(id: Long) {
        if(id in sockets) {
            sockets[id]!!.close(1000, null)
            sockets.remove(id)
        }
    }

    private fun stopStreaming() {
        for(sock in sockets) {
            sock.value.close(1000, null)
        }
        sockets.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        }

        notificationManager.cancel(1337)

        synchronized(serviceRunning) {
            serviceRunning = false
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if(intent.getBooleanExtra(KEY_STOP_STREAMING, false)) {
            Log.d(TAG, "Stream goes suya..")
            stopStreaming()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        var description = getString(R.string.streaming_notification_description)
        val accounts = accountManager.getAllAccountsOrderedByActive()
        var count = 0
        for(account in accounts) {
            stopStreamingForId(account.id)

            if(!account.notificationsStreamingEnabled)
                continue

            val endpoint = "wss://${account.domain}/api/v1/streaming/?access_token=${account.accessToken}&stream=user:notification"

            val request = Request.Builder().url(endpoint).build()
            val client = OkHttpClient.Builder().build()

            Log.d(TAG, "Running stream for ${account.fullName}")

            sockets[account.id] = client.newWebSocket(request, StreamingListener(this, gson, account))
            description += "\n" + account.fullName
            count++
        }

        if(count <= 0) {
            Log.d(TAG, "No accounts. Stopping stream")
            stopStreaming()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.streaming_notification_name), NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.streaming_notification_name))
                .setContentText(description)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(this, R.color.tusky_blue))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            startForeground(1337, builder.build())
        } else {
            notificationManager.notify(1337, builder.build())
        }

        synchronized(serviceRunning) {
            serviceRunning = true
        }

        return START_NOT_STICKY
    }

    companion object {
        val CHANNEL_ID = "streaming"
        val KEY_STOP_STREAMING = "stop_streaming"
        val TAG = "StreamingService"

        @JvmStatic
        var serviceRunning = false

        @JvmStatic
        private fun startForegroundService(ctx: Context, intent: Intent) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        @JvmStatic
        fun startStreaming(context: Context) {
            val intent = Intent(context, StreamingService::class.java)
            intent.putExtra(KEY_STOP_STREAMING, false)

            Log.d(TAG, "Starting notifications streaming service...")

            startForegroundService(context, intent)
        }

        @JvmStatic
        fun stopStreaming(context: Context) {
            synchronized(serviceRunning) {
                if(!serviceRunning)
                    return

                val intent = Intent(context, StreamingService::class.java)
                intent.putExtra(KEY_STOP_STREAMING, true)

                Log.d(TAG, "Stopping notifications streaming service...")

                serviceRunning = false

                startForegroundService(context, intent)
            }
        }
    }

    class StreamingListener(val context: Context, val gson: Gson, val account: AccountEntity) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Stream connected to: ${account.fullName}/user:notification")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Stream closed for: ${account.fullName}/user:notification")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = gson.fromJson(text, StreamEvent::class.java)
            when(event.event) {
                StreamEvent.EventType.NOTIFICATION -> {
                    val notification = gson.fromJson(event.payload, Notification::class.java)
                    NotificationHelper.make(context, notification, account, true)
                }
                else -> {
                    Log.d(TAG, "Unknown event type: ${event.event.toString()}")
                }
            }


            super.onMessage(webSocket, text)
        }

    }

}
