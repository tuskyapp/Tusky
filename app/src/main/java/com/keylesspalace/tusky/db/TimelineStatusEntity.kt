/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.keylesspalace.tusky.entity.FilterResult
import com.keylesspalace.tusky.entity.Status

/**
 * Entity for caching status data. Used within home timelines and notifications.
 * The information if a status is a reblog is not stored here but in [HomeTimelineEntity].
 */
@Entity(
    primaryKeys = ["serverId", "tuskyAccountId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["serverId", "tuskyAccountId"],
                childColumns = ["authorServerId", "tuskyAccountId"]
            )
        ]
        ),
    // Avoiding rescanning status table when accounts table changes. Recommended by Room(c).
    indices = [Index("authorServerId", "tuskyAccountId")]
)
@TypeConverters(Converters::class)
data class TimelineStatusEntity(
    // id never flips: we need it for sorting so it's a real id
    val serverId: String,
    val url: String?,
    // our local id for the logged in user in case there are multiple accounts per instance
    val tuskyAccountId: Long,
    val authorServerId: String,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val content: String,
    val createdAt: Long,
    val editedAt: Long?,
    val emojis: String?,
    val reblogsCount: Int,
    val favouritesCount: Int,
    val repliesCount: Int,
    val reblogged: Boolean,
    val bookmarked: Boolean,
    val favourited: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val visibility: Status.Visibility,
    val attachments: String?,
    val mentions: String?,
    val tags: String?,
    val application: String?,
    // if it has a reblogged status, it's id is stored here
    val poll: String?,
    val muted: Boolean?,
    /** Also used as the "loading" attribute when this TimelineStatusEntity is a placeholder */
    val expanded: Boolean,
    val contentCollapsed: Boolean,
    val contentShowing: Boolean,
    val pinned: Boolean,
    val card: String?,
    val language: String?,
    val filtered: List<FilterResult>?
)

@Entity(
    primaryKeys = ["serverId", "tuskyAccountId"]
)
data class TimelineAccountEntity(
    val serverId: String,
    val tuskyAccountId: Long,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val url: String,
    val avatar: String,
    val emojis: String,
    val bot: Boolean
)

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
