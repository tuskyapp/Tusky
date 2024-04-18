package com.keylesspalace.tusky.components.notifications

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.fakeAccount
import com.keylesspalace.tusky.components.timeline.fakeStatus
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.entity.NotificationDataEntity
import com.keylesspalace.tusky.db.entity.NotificationEntity
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import java.util.Date
import org.junit.Assert.assertEquals

fun fakeNotification(
    type: Notification.Type = Notification.Type.FAVOURITE,
    id: String = "1",
    account: TimelineAccount = fakeAccount(id = id),
    status: Status? = fakeStatus(id = id),
    report: Report? = null
) = Notification(
    type = type,
    id = id,
    account = account,
    status = status,
    report = report
)

fun fakeReport(
    id: String = "1",
    category: String = "spam",
    statusIds: List<String>? = null,
    createdAt: Date = Date(1712509983273),
    targetAccount: TimelineAccount = fakeAccount()
) = Report(
    id = id,
    category = category,
    statusIds = statusIds,
    createdAt = createdAt,
    targetAccount = targetAccount
)

fun Notification.toNotificationDataEntity(
    tuskyAccountId: Long,
    isStatusExpanded: Boolean = false,
    isStatusContentShowing: Boolean = false
) = NotificationDataEntity(
    tuskyAccountId = tuskyAccountId,
    type = type,
    id = id,
    account = account.toEntity(tuskyAccountId),
    status = status?.toEntity(
        tuskyAccountId = tuskyAccountId,
        expanded = isStatusExpanded,
        contentShowing = isStatusContentShowing,
        contentCollapsed = true
    ),
    statusAccount = status?.account?.toEntity(tuskyAccountId),
    report = report?.toEntity(tuskyAccountId),
    reportTargetAccount = report?.targetAccount?.toEntity(tuskyAccountId)
)

fun Placeholder.toNotificationDataEntity(
    tuskyAccountId: Long
) = NotificationDataEntity(
    tuskyAccountId = tuskyAccountId,
    type = null,
    id = id,
    account = null,
    status = null,
    statusAccount = null,
    report = null,
    reportTargetAccount = null
)

suspend fun AppDatabase.insert(notifications: List<Notification>, tuskyAccountId: Long = 1) = withTransaction {
    notifications.forEach { notification ->

        timelineAccountDao().insert(
            notification.account.toEntity(tuskyAccountId)
        )

        notification.report?.let { report ->
            timelineAccountDao().insert(
                report.targetAccount.toEntity(
                    tuskyAccountId = tuskyAccountId,
                )
            )
            notificationsDao().insertReport(report.toEntity(tuskyAccountId))
        }
        notification.status?.let { status ->
            timelineAccountDao().insert(
                status.account.toEntity(
                    tuskyAccountId = tuskyAccountId,
                )
            )
            timelineStatusDao().insert(
                status.toEntity(
                    tuskyAccountId = tuskyAccountId,
                    expanded = false,
                    contentShowing = false,
                    contentCollapsed = true
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

suspend fun AppDatabase.assertNotifications(
    expected: List<NotificationDataEntity>,
    tuskyAccountId: Long = 1
) {
    val pagingSource = notificationsDao().getNotifications(tuskyAccountId)

    val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

    val loaded = (loadResult as PagingSource.LoadResult.Page).data

    assertEquals(expected, loaded)
}
