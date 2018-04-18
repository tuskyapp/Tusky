/* Copyright 2018 Jeremiasz Nelz <remi6397(a)gmail.com>
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput
import android.support.v4.content.ContextCompat
import android.util.Log
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TuskyApplication
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.service.SendTootService
import com.keylesspalace.tusky.util.NotificationHelper
import dagger.android.AndroidInjection
import java.util.*
import javax.inject.Inject

class SendStatusBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onReceive(context: Context?, intent: Intent?) {
        AndroidInjection.inject(this, context)

        if (intent!!.action == NotificationHelper.REPLY_ACTION) {
            val message = getReplyMessage(intent)
            val notification = intent.getStringExtra(NotificationHelper.KEY_NOTIFICATION_ID)
            val sender = intent.getLongExtra(NotificationHelper.KEY_SENDER_ACCOUNT, -1)
            val citedStatus = intent.getLongExtra(NotificationHelper.KEY_CITED_STATUS, -1)
            val visibility = intent.getSerializableExtra(NotificationHelper.KEY_VISIBILITY) as Status.Visibility
            val spoiler = intent.getStringExtra(NotificationHelper.KEY_SPOILER)
            val mentions = intent.getStringArrayExtra(NotificationHelper.KEY_MENTIONS)

            val account = accountManager.getAccountById(sender)

            val sendIntent = SendTootService.sendTootIntent(context!!, mentions.map { "@$it" }.joinToString(" ") + " " + message.toString(), spoiler,
                    visibility, false, Arrays.asList(), Arrays.asList(), citedStatus.toString(),
                    null,
                    null,
                    null, account!!, 0)

            context.startService(sendIntent)

            val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MENTION + account.identifier)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(ContextCompat.getColor(context, (R.color.primary)))
                    .setGroup(account.accountId)
                    .setDefaults(0) // So it doesn't ring twice, notify only in Target callback

            builder.setContentTitle(context.getString(R.string.status_sent))
            builder.setContentText(context.getString(R.string.status_sent_long))

            builder.setSubText(account.fullName);
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            builder.setCategory(NotificationCompat.CATEGORY_SOCIAL);
            builder.setOnlyAlertOnce(true);

            val notificationManager = NotificationManagerCompat.from(context)

            notificationManager.notify(notification.toInt(), builder.build())
        }
    }

    private fun getReplyMessage(intent: Intent): CharSequence {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)

        return remoteInput.getCharSequence(NotificationHelper.KEY_REPLY);
    }

}