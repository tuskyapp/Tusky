/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.components.notifications

import com.google.gson.Gson
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toAccount
import com.keylesspalace.tusky.components.timeline.toStatus
import com.keylesspalace.tusky.db.NotificationDataEntity
import com.keylesspalace.tusky.db.NotificationEntity
import com.keylesspalace.tusky.db.NotificationReportEntity
import com.keylesspalace.tusky.db.TimelineAccountEntity
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData

fun Placeholder.toNotificationEntity(tuskyAccountId: Long): NotificationEntity {
    return NotificationEntity(
        id = this.id,
        tuskyAccountId = tuskyAccountId,
        type = null,
        accountId = null,
        statusId = null,
        reportId = null,
        loading = loading
    )
}

fun Notification.toEntity(
    tuskyAccountId: Long
) = NotificationEntity(
    tuskyAccountId = tuskyAccountId,
    type = type,
    id = id,
    accountId = account.id,
    statusId = status?.id,
    reportId = report?.id,
    loading = false
)

fun Report.toEntity(
    tuskyAccountId: Long
) = NotificationReportEntity(
    tuskyAccountId = tuskyAccountId,
    serverId = id,
    category = category,
    statusIds = statusIds,
    createdAt = createdAt,
    targetAccountId = targetAccount.id
)

fun NotificationDataEntity.toViewData(
    gson: Gson,
    translation: TranslationViewData? = null
): NotificationViewData {
    if (type == null) {
        return NotificationViewData.Placeholder(id = id, isLoading = loading)
    }

    return NotificationViewData.Concrete(
        id = id,
        type = type,
        account = account.toAccount(gson),
        statusViewData = if (status != null && statusAccount != null) {
            StatusViewData.Concrete(
                status = status.toStatus(gson, statusAccount),
                isExpanded = this.status.expanded,
                isShowingContent = this.status.contentShowing,
                isCollapsed = this.status.contentCollapsed,
                translation = translation
            )
        } else {
            null
        },
        report = if (report != null && reportTargetAccount != null) {
            report.toReport(reportTargetAccount, gson)
        } else {
            null
        }
    )
}

fun NotificationReportEntity.toReport(
    account: TimelineAccountEntity,
    gson: Gson
) = Report(
    id = serverId,
    category = category,
    statusIds = statusIds,
    createdAt = createdAt,
    targetAccount = account.toAccount(gson)
)
