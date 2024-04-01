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

package com.keylesspalace.tusky.db.dao

import androidx.room.Dao
import androidx.room.Transaction
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.entity.HomeTimelineEntity
import com.keylesspalace.tusky.db.entity.NotificationEntity
import com.keylesspalace.tusky.db.entity.NotificationReportEntity
import com.keylesspalace.tusky.db.entity.TimelineAccountEntity
import com.keylesspalace.tusky.db.entity.TimelineStatusEntity

@Dao
abstract class CleanupDao(
    private val db: AppDatabase
) {
    /**
     * Cleans the [HomeTimelineEntity], [TimelineStatusEntity], [TimelineAccountEntity], [NotificationEntity] and [NotificationReportEntity] tables from old entries.
     * @param tuskyAccountId id of the account for which to clean tables
     * @param timelineLimit how many timeline items to keep
     * @param notificationLimit how many notifications to keep
     */
    @Transaction
    open suspend fun cleanupOldData(
        tuskyAccountId: Long,
        timelineLimit: Int,
        notificationLimit: Int
    ) {
        // the order here is important - foreign key constraints must not be violated
        db.notificationsDao().cleanupNotifications(tuskyAccountId, notificationLimit)
        db.notificationsDao().cleanupReports(tuskyAccountId)
        db.timelineDao().cleanupHomeTimeline(tuskyAccountId, timelineLimit)
        db.timelineStatusDao().cleanupStatuses(tuskyAccountId)
        db.timelineAccountDao().cleanupAccounts(tuskyAccountId)
    }

    @Transaction
    open suspend fun cleanupEverything(tuskyAccountId: Long) {
        // the order here is important - foreign key constraints must not be violated
        db.notificationsDao().removeAllNotifications(tuskyAccountId)
        db.notificationsDao().removeAllReports(tuskyAccountId)
        db.timelineDao().removeAllHomeTimelineItems(tuskyAccountId)
        db.timelineStatusDao().removeAllStatuses(tuskyAccountId)
        db.timelineAccountDao().removeAllAccounts(tuskyAccountId)
    }
}
