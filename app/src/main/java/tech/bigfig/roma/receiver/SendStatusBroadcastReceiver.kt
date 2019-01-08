/* Copyright 2018 Jeremiasz Nelz <remi6397(a)gmail.com>
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import android.util.Log
import tech.bigfig.roma.ComposeActivity
import tech.bigfig.roma.R
import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.entity.Status
import tech.bigfig.roma.service.SendTootService
import tech.bigfig.roma.util.NotificationHelper
import dagger.android.AndroidInjection
import java.util.*
import javax.inject.Inject

private const val TAG = "SendStatusBR"

class SendStatusBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val notificationId = intent.getIntExtra(NotificationHelper.KEY_NOTIFICATION_ID, -1)
        val senderId = intent.getLongExtra(NotificationHelper.KEY_SENDER_ACCOUNT_ID, -1)
        val senderIdentifier = intent.getStringExtra(NotificationHelper.KEY_SENDER_ACCOUNT_IDENTIFIER)
        val senderFullName = intent.getStringExtra(NotificationHelper.KEY_SENDER_ACCOUNT_FULL_NAME)
        val citedStatusId = intent.getStringExtra(NotificationHelper.KEY_CITED_STATUS_ID)
        val visibility = intent.getSerializableExtra(NotificationHelper.KEY_VISIBILITY) as Status.Visibility
        val spoiler = intent.getStringExtra(NotificationHelper.KEY_SPOILER)
        val mentions = intent.getStringArrayExtra(NotificationHelper.KEY_MENTIONS)
        val citedText = intent.getStringExtra(NotificationHelper.KEY_CITED_TEXT)
        val localAuthorId = intent.getStringExtra(NotificationHelper.KEY_CITED_AUTHOR_LOCAL)

        val account = accountManager.getAccountById(senderId)

        val notificationManager = NotificationManagerCompat.from(context)


        if (intent.action == NotificationHelper.REPLY_ACTION) {

            val message = getReplyMessage(intent)

            if (account == null) {
                Log.w(TAG, "Account \"$senderId\" not found in database. Aborting quick reply!")

                val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MENTION + senderIdentifier)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setColor(ContextCompat.getColor(context, (R.color.roma_blue)))
                        .setGroup(senderFullName)
                        .setDefaults(0) // So it doesn't ring twice, notify only in Target callback

                builder.setContentTitle(context.getString(R.string.error_generic))
                builder.setContentText(context.getString(R.string.error_sender_account_gone))

                builder.setSubText(senderFullName)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
                builder.setOnlyAlertOnce(true)

                notificationManager.notify(notificationId, builder.build())
            } else {
                val text = mentions.joinToString(" ", postfix = " ") { "@$it" } + message.toString()

                val sendIntent = SendTootService.sendTootIntent(
                        context,
                        text,
                        spoiler,
                        visibility,
                        false,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        citedStatusId,
                        null,
                        null,
                        null, account, 0)

                context.startService(sendIntent)

                val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MENTION + senderIdentifier)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setColor(ContextCompat.getColor(context, (R.color.roma_blue)))
                        .setGroup(senderFullName)
                        .setDefaults(0) // So it doesn't ring twice, notify only in Target callback

                builder.setContentTitle(context.getString(R.string.status_sent))
                builder.setContentText(context.getString(R.string.status_sent_long))

                builder.setSubText(senderFullName)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
                builder.setOnlyAlertOnce(true)

                notificationManager.notify(notificationId, builder.build())
            }
        } else if (intent.action == NotificationHelper.COMPOSE_ACTION) {

            context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

            notificationManager.cancel(notificationId)

            accountManager.setActiveAccount(senderId)

            val composeIntent = ComposeActivity.IntentBuilder()
                    .inReplyToId(citedStatusId)
                    .replyVisibility(visibility)
                    .contentWarning(spoiler)
                    .mentionedUsernames(Arrays.asList(*mentions))
                    .repyingStatusAuthor(localAuthorId)
                    .replyingStatusContent(citedText)
                    .build(context)

            context.startActivity(composeIntent)
        }
    }

    private fun getReplyMessage(intent: Intent): CharSequence {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)

        return remoteInput.getCharSequence(NotificationHelper.KEY_REPLY, "")
    }

}