/* Copyright 2018 Tusky contributors
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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.service.SendStatusService
import com.keylesspalace.tusky.service.StatusToSend
import com.keylesspalace.tusky.util.getSerializableExtraCompat
import com.keylesspalace.tusky.util.randomAlphanumericString
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SendStatusBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountManager: AccountManager

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationService.REPLY_ACTION) {
            val serverNotificationId = intent.getStringExtra(NotificationService.KEY_SERVER_NOTIFICATION_ID)
            val senderId = intent.getLongExtra(NotificationService.KEY_SENDER_ACCOUNT_ID, -1)
            val senderIdentifier = intent.getStringExtra(
                NotificationService.KEY_SENDER_ACCOUNT_IDENTIFIER
            )
            val senderFullName = intent.getStringExtra(
                NotificationService.KEY_SENDER_ACCOUNT_FULL_NAME
            )
            val citedStatusId = intent.getStringExtra(NotificationService.KEY_CITED_STATUS_ID)
            val visibility =
                intent.getSerializableExtraCompat<Status.Visibility>(NotificationService.KEY_VISIBILITY)!!
            val spoiler = intent.getStringExtra(NotificationService.KEY_SPOILER).orEmpty()
            val mentions = intent.getStringArrayExtra(NotificationService.KEY_MENTIONS).orEmpty()

            val account = accountManager.getAccountById(senderId)

            val notificationManager = NotificationManagerCompat.from(context)

            val message = getReplyMessage(intent)

            if (account == null) {
                Log.w(TAG, "Account \"$senderId\" not found in database. Aborting quick reply!")

                val notification = NotificationCompat.Builder(
                    context,
                    NotificationService.CHANNEL_MENTION + senderIdentifier
                )
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(context.getColor(R.color.tusky_blue))
                    .setGroup(senderFullName)
                    .setDefaults(0) // We don't want this to make any sound or vibration
                    .setOnlyAlertOnce(true)
                    .setContentTitle(context.getString(R.string.error_generic))
                    .setContentText(context.getString(R.string.error_sender_account_gone))
                    .setSubText(senderFullName)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .build()

                notificationManager.notify(serverNotificationId, senderId.toInt(), notification)
            } else {
                val text = mentions.joinToString(" ", postfix = " ") { "@$it" } + message.toString()

                val sendIntent = SendStatusService.sendStatusIntent(
                    context,
                    StatusToSend(
                        text = text,
                        warningText = spoiler,
                        visibility = visibility.stringValue,
                        sensitive = false,
                        media = emptyList(),
                        scheduledAt = null,
                        inReplyToId = citedStatusId,
                        poll = null,
                        replyingStatusContent = null,
                        replyingStatusAuthorUsername = null,
                        accountId = account.id,
                        draftId = -1,
                        idempotencyKey = randomAlphanumericString(16),
                        retries = 0,
                        language = null,
                        statusId = null
                    )
                )

                context.startService(sendIntent)

                // Notifications with remote input active can't be cancelled, so let's replace it with another one that will dismiss automatically
                val notification = NotificationCompat.Builder(
                    context,
                    NotificationService.CHANNEL_MENTION + senderIdentifier
                )
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(context.getColor(R.color.notification_color))
                    .setGroup(senderFullName)
                    .setDefaults(0) // We don't want this to make any sound or vibration
                    .setOnlyAlertOnce(true)
                    .setContentTitle(context.getString(R.string.reply_sending))
                    .setContentText(context.getString(R.string.reply_sending_long))
                    .setSubText(senderFullName)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setTimeoutAfter(5000)
                    .build()

                notificationManager.notify(serverNotificationId, senderId.toInt(), notification)
            }
        }
    }

    private fun getReplyMessage(intent: Intent): CharSequence {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)

        return remoteInput?.getCharSequence(NotificationService.KEY_REPLY, "") ?: ""
    }

    companion object {
        const val TAG = "SendStatusBroadcastReceiver"
    }
}
