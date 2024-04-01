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

/**
 * Entity to store an item on the home timeline. Can be a standalone status, a reblog, or a placeholder.
 */
@Entity(
    primaryKeys = ["id", "tuskyAccountId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = TimelineStatusEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["statusId", "tuskyAccountId"]
            ),
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["reblogAccountId", "tuskyAccountId"]
            )
        ]
        ),
    indices = [
        Index("statusId", "tuskyAccountId"),
        Index("reblogAccountId", "tuskyAccountId"),
    ]
)
data class HomeTimelineEntity(
    val tuskyAccountId: Long,
    // the id by which the timeline is sorted
    val id: String,
    // the id of the status, null when a placeholder
    val statusId: String?,
    // the id of the account who reblogged the status, null if no reblog
    val reblogAccountId: String?,
    // only relevant when this is a placeholder
    val loading: Boolean = false
)

/**
 * Helper class for queries that return HomeTimelineEntity including all references
 */
data class HomeTimelineData(
    val id: String,
    @Embedded val status: TimelineStatusEntity?,
    @Embedded(prefix = "a_") val account: TimelineAccountEntity?,
    @Embedded(prefix = "rb_") val reblogAccount: TimelineAccountEntity?,
    val loading: Boolean
)
