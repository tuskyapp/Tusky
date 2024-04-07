package com.keylesspalace.tusky.components.notifications

import androidx.room.withTransaction
import com.keylesspalace.tusky.components.timeline.mockAccount
import com.keylesspalace.tusky.components.timeline.mockStatus
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.entity.NotificationEntity
import com.keylesspalace.tusky.di.NetworkModule
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import java.util.Date

fun mockNotification(
    type: Notification.Type = Notification.Type.FAVOURITE,
    id: String = "1",
    account: TimelineAccount = mockAccount(),
    status: Status? = mockStatus(),
    report: Report? = null
) = Notification(
    type = type,
    id = id,
    account = account,
    status = status,
    report = report
)

fun mockReport(
    id: String = "1",
    category: String = "spam",
    statusIds: List<String>? = null,
    createdAt: Date = Date(1712509983273),
    targetAccount: TimelineAccount = mockAccount()
) = Report(
    id = id,
    category = category,
    statusIds = statusIds,
    createdAt = createdAt,
    targetAccount = targetAccount
)

suspend fun AppDatabase.insert(notifications: List<Notification>, tuskyAccountId: Long = 1) = withTransaction {
    val moshi = NetworkModule.providesMoshi()
    notifications.forEach { notification ->

        timelineAccountDao().insert(
            notification.account.toEntity(tuskyAccountId, moshi)
        )

        notification.report?.let { report ->
            timelineAccountDao().insert(
                report.targetAccount.toEntity(
                    tuskyAccountId = tuskyAccountId,
                    moshi = moshi
                )
            )
            notificationsDao().insertReport(report.toEntity(tuskyAccountId))
        }
        notification.status?.let { status ->
            timelineAccountDao().insert(
                status.account.toEntity(
                    tuskyAccountId = tuskyAccountId,
                    moshi = moshi
                )
            )
            timelineStatusDao().insert(
                status.toEntity(
                    tuskyAccountId = tuskyAccountId,
                    moshi = moshi,
                    expanded = false,
                    contentShowing = false,
                    contentCollapsed = false
                )
            )
        }
        notificationsDao().insertNotification(
            NotificationEntity(
                tuskyAccountId = tuskyAccountId,
                type = notification.type,
                id = notification.id,
                accountId = notification.account.id,
                statusId = notification.status?.id,
                reportId = notification.report?.id,
                loading = false
            )
        )
    }
}
