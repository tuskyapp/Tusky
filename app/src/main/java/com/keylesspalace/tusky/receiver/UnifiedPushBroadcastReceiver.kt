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
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.keylesspalace.tusky.components.notifications.NotificationWorker
import com.keylesspalace.tusky.components.notifications.registerUnifiedPushEndpoint
import com.keylesspalace.tusky.components.notifications.unregisterUnifiedPushEndpoint
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.network.MastodonApi
import dagger.android.AndroidInjection
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver
import javax.inject.Inject

@DelicateCoroutinesApi
class UnifiedPushBroadcastReceiver : MessagingReceiver() {
    companion object {
        const val TAG = "UnifiedPush"
    }

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        AndroidInjection.inject(this, context)
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        AndroidInjection.inject(this, context)
        Log.d(TAG, "New message received for account $instance")
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequest.from(NotificationWorker::class.java)
        workManager.enqueue(request)
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        AndroidInjection.inject(this, context)
        Log.d(TAG, "Endpoint available for account $instance: $endpoint")
        accountManager.getAccountById(instance.toLong())?.let {
            // Launch the coroutine in global scope -- it is short and we don't want to lose the registration event
            // and there is no saner way to use structured concurrency in a receiver
            GlobalScope.launch { registerUnifiedPushEndpoint(context, mastodonApi, accountManager, it, endpoint) }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) = Unit

    override fun onUnregistered(context: Context, instance: String) {
        AndroidInjection.inject(this, context)
        Log.d(TAG, "Endpoint unregistered for account $instance")
        accountManager.getAccountById(instance.toLong())?.let {
            // It's fine if the account does not exist anymore -- that means it has been logged out
            GlobalScope.launch { unregisterUnifiedPushEndpoint(mastodonApi, accountManager, it) }
        }
    }
}
