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
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.keylesspalace.tusky.db.entity.TimelineAccountEntity

@Dao
abstract class TimelineAccountDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insert(timelineAccountEntity: TimelineAccountEntity): Long

    @Query(
        """SELECT * FROM TimelineAccountEntity a
           WHERE a.serverId = :accountId
           AND a.tuskyAccountId = :tuskyAccountId"""
    )
    internal abstract suspend fun getAccount(tuskyAccountId: Long, accountId: String): TimelineAccountEntity?

    @Query("DELETE FROM TimelineAccountEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun removeAllAccounts(tuskyAccountId: Long)

    /**
     * Cleans the TimelineAccountEntity table from accounts that are no longer referenced by either TimelineStatusEntity, HomeTimelineEntity or NotificationEntity
     * @param tuskyAccountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """DELETE FROM TimelineAccountEntity WHERE tuskyAccountId = :tuskyAccountId
        AND serverId NOT IN
        (SELECT authorServerId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId)
        AND serverId NOT IN
        (SELECT reblogAccountId FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND reblogAccountId IS NOT NULL)
        AND serverId NOT IN
        (SELECT accountId FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND accountId IS NOT NULL)"""
    )
    abstract suspend fun cleanupAccounts(tuskyAccountId: Long)
}
