package com.keylesspalace.tusky

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.NewHomeTimelineStatusEvent
import com.keylesspalace.tusky.appstore.NewNotificationEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.NotificationHelper
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import retrofit2.Call
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val TAG = "ProfileStreamListener"

private data class Optional<T>(val value: T)

/**
 * Subscribes to the profile events using SSE.
 */
class ProfileStreamListener @Inject constructor(
        private val api: MastodonApi,
        private val gson: Gson,
        private val context: Context,
        private val accountManager: com.keylesspalace.tusky.db.AccountManager,
        private val eventHub: EventHub,
        lifecycleOwner: LifecycleOwner
) : LifecycleObserver {

    private val disposable = CompositeDisposable()
    private var failedAttempts = 0
    private var isStoppedManually = true
    private val call: AtomicReference<Call<*>?> = AtomicReference(null)

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun resume() {
        Log.d(TAG, "resume")
        this.isStoppedManually = false
        this.internalResume()
    }

    fun stop() {
        Log.d(TAG, "pause")
        this.isStoppedManually = true
        this.internalStop()
    }

    private fun internalResume() {
        Log.d(TAG, "internal resume")
        if (this.isStoppedManually) {
            Log.d(TAG, "Was stopped manually, not resumming")
            return
        }
        Single.fromCallable {
            val call = api.userStream()
            // If it wasn't null, don't do anything
            if (!this.call.compareAndSet(null, call)) {
                Log.d(TAG, "internal resume cancelled: ther was a call alrady")
                return@fromCallable Optional(null)
            }
            call.execute().body()!!.charStream().useLines { linesSequence ->
                // Mastodon Event types: update, notification, delete, filters_changed
                // we react only on notification for now
                // To detect that it's a notification, we should notice that previous line was
                // "event: notification"
                // Then notification will be on the next line as:
                // "data: {...}"
                var lastEventType: String? = null
                linesSequence.forEach { line ->
                    failedAttempts = 0
                    val parts = line.split(": ", limit = 2)
                    if (parts.size < 2) {
                        return@forEach
                    }
                    when (parts[0]) {
                        "event" -> lastEventType = parts[1]
                        "data" -> when (lastEventType) {
                            null -> {
                            }
                            "notification" -> {
                                Log.d(TAG, "new notification")
                                val account = accountManager.activeAccount
                                if (account != null) {
                                    val notification = gson.fromJson(parts[1], Notification::class.java)
                                    NotificationHelper.make(context, notification, account, true)
                                    account.lastNotificationId = notification.id
                                    accountManager.saveAccount(account)
                                    eventHub.dispatch(NewNotificationEvent(notification))
                                }
                            }
                            "update" -> {
                                Log.d(TAG, "new update")
                                accountManager.activeAccount?.let { account ->
                                    val status = gson.fromJson(parts[1], Status::class.java)
                                    eventHub.dispatch(NewHomeTimelineStatusEvent(status))
                                }
                            }
                            "delete" -> {
                                Log.d(TAG, "new delete")
                                eventHub.dispatch(StatusDeletedEvent(parts[1]))
                            }
                        }
                    }
                }
            }
            Optional(call)
        }
                .retryWhen { attempts ->
                    attempts.flatMap { error ->
                        this.failedAttempts++
                        val existingCall = this.call.get()
                        Log.d(TAG, "Error, existing call: ${existingCall != null}")
                        if (failedAttempts < 10 && existingCall != null) {
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
                .subscribe { maybeCall, err ->
                    // Override it with null if it's the same call
                    val wasThisCall = err == null && this.call.compareAndSet(maybeCall.value, null)
                    if (wasThisCall) {
                        Log.w(TAG, "Error while listening to profile stream")
                    }
                }
                .addTo(disposable)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onResume() {
        Log.d(TAG, "onResume")
        this.internalResume()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun onPause() {
        Log.d(TAG, "onPause")
        this.internalStop()
    }

    private fun internalStop() {
        // Set it to null. Cancel it if it was there.
        this.call.getAndSet(null)
                ?.also { previous ->
                    Log.d(TAG, "internal stop, previous call: $previous")
                }?.cancel()
    }
}