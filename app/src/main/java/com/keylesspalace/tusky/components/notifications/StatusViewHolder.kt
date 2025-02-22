/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.notifications

import android.view.View
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusViewHolder
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.NotificationViewData

internal class StatusViewHolder(
    itemView: View,
    private val statusActionListener: StatusActionListener,
    private val accountId: String
) : NotificationsViewHolder, StatusViewHolder(itemView) {

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val statusViewData = viewData.statusViewData
        if (statusViewData == null) {
            /* in some very rare cases servers sends null status even though they should not */
            showStatusContent(false)
        } else {
            if (payloads.isEmpty()) {
                showStatusContent(true)
            }
            setupWithStatus(
                statusViewData,
                statusActionListener,
                statusDisplayOptions,
                payloads,
                false
            )
            if (payloads.isNotEmpty()) {
                return
            }

            if (viewData.type == Notification.Type.Poll) {
                statusInfo.setText(if (accountId == viewData.account.id) R.string.poll_ended_created else R.string.poll_ended_voted)
                statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0)
                statusInfo.setCompoundDrawablePadding(Utils.dpToPx(statusInfo.context, 10))
                statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.context, 28), 0, 0, 0)
                statusInfo.show()
            } else if (viewData.type == Notification.Type.Mention) {
                statusInfo.setCompoundDrawablePadding(Utils.dpToPx(statusInfo.context, 6))
                statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.context, 38), 0, 0, 0)
                statusInfo.show()
                if (viewData.statusViewData.status.inReplyToAccountId == accountId) {
                    statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_reply_18dp, 0, 0, 0)

                    if (viewData.statusViewData.status.visibility == Status.Visibility.DIRECT) {
                        statusInfo.setText(R.string.notification_info_reply)
                    } else {
                        statusInfo.setText(R.string.notification_info_private_reply)
                    }
                } else {
                    statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_at_18dp, 0, 0, 0)

                    if (viewData.statusViewData.status.visibility == Status.Visibility.DIRECT) {
                        statusInfo.setText(R.string.notification_info_private_mention)
                    } else {
                        statusInfo.setText(R.string.notification_info_mention)
                    }
                }
            } else {
                hideStatusInfo()
            }
        }
    }
}
