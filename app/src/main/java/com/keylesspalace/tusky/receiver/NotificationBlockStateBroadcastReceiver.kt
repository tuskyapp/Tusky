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

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.keylesspalace.tusky.components.notifications.PushNotificationManager
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.ApplicationScope
import com.keylesspalace.tusky.network.MastodonApi
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

/**
 * This listens for changed notification channel settings (from the Android system) and updates an account's push
 * subscription if active.
 */
@DelicateCoroutinesApi
class NotificationBlockStateBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    @ApplicationScope
    lateinit var externalScope: CoroutineScope

    @Inject
    lateinit var notificationManager: PushNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)
        if (Build.VERSION.SDK_INT < 28) return
        if (!notificationManager.canEnablePushNotifications()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val gid = when (intent.action) {
            NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED -> {
                val channelId = intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID)
                nm.getNotificationChannel(channelId).group
            }
            NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED -> {
                intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_GROUP_ID)
            }
            else -> null
        } ?: return

        accountManager.getAccountByIdentifier(gid)?.let { account ->
            // TODO how did the changed (system) setting end up in the account object here (for example in field AccountEntity:notificationsMentioned)?

            if (notificationManager.hasPushNotificationsEnabled(account)) {
                // Update UnifiedPush notification subscription
                externalScope.launch { notificationManager.updateUnifiedPushSubscription(account) }
            }
        }
    }
}
