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
import android.util.Log
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.ApplicationScope
import com.keylesspalace.tusky.network.MastodonApi
import dagger.hilt.android.AndroidEntryPoint
import java.nio.charset.Charset
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver

@AndroidEntryPoint
class UnifiedPushBroadcastReceiver : MessagingReceiver() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Log.d(TAG, "New message received for account $instance: #${message.size} ${String(message, Charset.forName("utf-16")).substring(0,20)}")
        notificationService.fetchNotificationsOnPushMessage(accountManager.getAccountById(instance.toLong()))
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Log.d(TAG, "Endpoint available for account $instance: $endpoint")
        accountManager.getAccountById(instance.toLong())?.let {
            applicationScope.launch { notificationService.registerPushEndpoint(it, endpoint) }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) = Unit

    override fun onUnregistered(context: Context, instance: String) {
        Log.d(TAG, "Endpoint unregistered for account $instance")
        accountManager.getAccountById(instance.toLong())?.let {
            // It's fine if the account does not exist anymore -- that means it has been logged out
            applicationScope.launch { notificationService.unregisterPushEndpoint(it) }
        }
    }

    companion object {
        const val TAG = "UnifiedPushBroadcastReceiver"
    }
}
