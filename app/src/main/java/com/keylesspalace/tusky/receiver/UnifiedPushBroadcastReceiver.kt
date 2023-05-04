/* Copyright 2022 Tusky contributors
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.keylesspalace.tusky.components.notifications.PushNotificationManager
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.ApplicationScope
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.worker.NotificationWorker
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver

@DelicateCoroutinesApi
class UnifiedPushBroadcastReceiver : MessagingReceiver() {
    companion object {
        const val TAG = "UnifiedPush"
    }

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var pushNotificationManager: PushNotificationManager

    @Inject
    @ApplicationScope
    lateinit var externalScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        AndroidInjection.inject(this, context)
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        AndroidInjection.inject(this, context)
        Log.d(TAG, "New message received for account $instance")

        val data = Data.Builder()
        data.putLong(NotificationWorker.KEY_ACCOUNT_ID, instance.toLongOrNull() ?: 0)

        val request = OneTimeWorkRequest
            .Builder(NotificationWorker::class.java)
            .setInputData(data.build())
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request)

        // Do we want a rate limiting here? I think, yes.
        //   At least it puts network load on as long as the push notifications are not shown directly.
        //   And after that it should still be a setting.
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        AndroidInjection.inject(this, context)
        Log.d(TAG, "Endpoint available for account $instance: $endpoint")
        accountManager.getAccountById(instance.toLong())?.let {
            externalScope.launch { pushNotificationManager.registerUnifiedPushEndpoint(it, endpoint) }
        }
    }

    // TODO hm?
    override fun onRegistrationFailed(context: Context, instance: String) = Unit

    override fun onUnregistered(context: Context, instance: String) {
        AndroidInjection.inject(this, context)
        Log.d(TAG, "Endpoint unregistered for account $instance")
        accountManager.getAccountById(instance.toLong())?.let {
            // It's fine if the account does not exist anymore -- that means it has been logged out
            externalScope.launch { pushNotificationManager.unregisterUnifiedPushEndpoint(it) }
        }
    }
}
