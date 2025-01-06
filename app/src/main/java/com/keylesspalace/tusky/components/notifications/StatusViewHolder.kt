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
import com.keylesspalace.tusky.adapter.StatusViewHolder
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
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
                payloads.firstOrNull(),
                false
            )
        }
        if (viewData.type == Notification.Type.POLL) {
            setPollInfo(accountId == viewData.account.id)
        } else {
            hideStatusInfo()
        }
    }
}
