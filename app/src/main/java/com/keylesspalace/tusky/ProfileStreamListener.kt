package com.keylesspalace.tusky

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.NewNotificationEvent
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.NotificationHelper
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "ProfileStreamListener"

/**
 * Subscribes to the profile events using SSE.
 */
class ProfileStreamListener @Inject constructor(
        api: MastodonApi,
        gson: Gson,
        context: Context,
        accountManager: com.keylesspalace.tusky.db.AccountManager,
        eventHub: EventHub
) {
    private val disposable: Disposable = Single
            .fromCallable {
                // TODO: listen for the network state
                api.userStream().execute().body()!!.charStream().useLines { linesSequence ->
                    // Mastodon Event types: update, notification, delete, filters_changed
                    // we react only on notification for now
                    // To detect that it's a notification, we should notice that previous line was
                    // "event: notification"
                    // Then notification will be on the next line as:
                    // "data: {...}"
                    var hadNotification = false
                    linesSequence.forEach { line ->
                        failedAttempts = 0
                        val parts = line.split(':', limit = 2)
                        if (parts.size > 1 && parts[0] == "data" && hadNotification) {
                            val account = accountManager.activeAccount
                            if (account != null) {
                                val notification = gson.fromJson(parts[1], Notification::class.java)
                                NotificationHelper.make(context, notification, account, true)
                                account.lastNotificationId = notification.id
                                accountManager.saveAccount(account)
                                eventHub.dispatch(NewNotificationEvent(notification))
                            }
                        }
                        hadNotification = line.startsWith("event: notification")
                    }
                }
            }
            .retryWhen { attempts ->
                attempts.flatMap { error ->
                    this.failedAttempts++
                    if (failedAttempts < 10) {
                        val delay = failedAttempts * 10L
                        Log.d(TAG, "Error while listening to profile stream, trying after $delay",
                                error)
                        Flowable.timer(delay, TimeUnit.SECONDS)
                    } else {
                        Flowable.error(error)
                    }
                }
            }
            .subscribeOn(Schedulers.newThread())
            .subscribe { _, err ->
                if (err != null) {
                    Log.w(TAG, "Error while listening to profile stream", err)
                }
            }

    private var failedAttempts = 0

    fun stop() {
        this.disposable.dispose()
    }

}