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
import com.keylesspalace.tusky.components.notifications.canEnablePushNotifications
import com.keylesspalace.tusky.components.notifications.isUnifiedPushNotificationEnabledForAccount
import com.keylesspalace.tusky.components.notifications.updateUnifiedPushSubscription
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.network.MastodonApi
import dagger.android.AndroidInjection
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@DelicateCoroutinesApi
class NotificationBlockStateBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)
        if (Build.VERSION.SDK_INT < 28) return
        if (!canEnablePushNotifications(context, accountManager)) return

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
            if (isUnifiedPushNotificationEnabledForAccount(account)) {
                // Update UnifiedPush notification subscription
                GlobalScope.launch { updateUnifiedPushSubscription(context, mastodonApi, accountManager, account) }
            }
        }
    }
}
