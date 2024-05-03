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

package com.keylesspalace.tusky.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.entity.Notification
import java.util.Date

data class NotificationDataEntity(
    // id of the account logged into Tusky this notifications belongs to
    val tuskyAccountId: Long,
    // null when placeholder
    val type: Notification.Type?,
    val id: String,
    @Embedded(prefix = "a_") val account: TimelineAccountEntity?,
    @Embedded(prefix = "s_") val status: TimelineStatusEntity?,
    @Embedded(prefix = "sa_") val statusAccount: TimelineAccountEntity?,
    @Embedded(prefix = "r_") val report: NotificationReportEntity?,
    @Embedded(prefix = "ra_") val reportTargetAccount: TimelineAccountEntity?,
    // relevant when it is a placeholder
    val loading: Boolean = false
)

@Entity(
    primaryKeys = ["id", "tuskyAccountId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["accountId", "tuskyAccountId"]
            ),
            ForeignKey(
                entity = TimelineStatusEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["statusId", "tuskyAccountId"]
            ),
            ForeignKey(
                entity = NotificationReportEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["reportId", "tuskyAccountId"]
            )
        ]
        ),
    indices = [
        Index("accountId", "tuskyAccountId"),
        Index("statusId", "tuskyAccountId"),
        Index("reportId", "tuskyAccountId"),
    ]
)
@TypeConverters(Converters::class)
data class NotificationEntity(
    // id of the account logged into Tusky this notifications belongs to
    val tuskyAccountId: Long,
    // null when placeholder
    val type: Notification.Type?,
    val id: String,
    val accountId: String?,
    val statusId: String?,
    val reportId: String?,
    // relevant when it is a placeholder
    val loading: Boolean = false
)

@Entity(
    primaryKeys = ["serverId", "tuskyAccountId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["targetAccountId", "tuskyAccountId"]
            )
        ]
        ),
    indices = [
        Index("targetAccountId", "tuskyAccountId"),
    ]
)
@TypeConverters(Converters::class)
data class NotificationReportEntity(
    // id of the account logged into Tusky this report belongs to
    val tuskyAccountId: Long,
    val serverId: String,
    val category: String,
    val statusIds: List<String>?,
    val createdAt: Date,
    val targetAccountId: String?
)
