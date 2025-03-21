/* Copyright 2018 Conny Duck
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
import com.keylesspalace.tusky.db.entity.NotificationPolicyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationPolicyDao {
    @Query("SELECT * FROM NotificationPolicyEntity WHERE tuskyAccountId = :accountId")
    fun notificationPolicyForAccount(accountId: Long): Flow<NotificationPolicyEntity?>

    @Insert(onConflict = REPLACE)
    suspend fun update(entity: NotificationPolicyEntity)

    @Query(
        "UPDATE NotificationPolicyEntity " +
            "SET pendingRequestsCount = max(0, pendingRequestsCount - 1)," +
            "pendingNotificationsCount = max(0, pendingNotificationsCount - :notificationCount) " +
            "WHERE tuskyAccountId = :accountId"
    )
    suspend fun updateCounts(
        accountId: Long,
        notificationCount: Int
    )

    @Query("DELETE FROM NotificationPolicyEntity WHERE tuskyAccountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}
