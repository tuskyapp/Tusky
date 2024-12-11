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

package com.keylesspalace.tusky.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.FilterResult
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PreviewCard
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
    val emojis: List<Emoji>,
    val reblogsCount: Int,
    val favouritesCount: Int,
    val repliesCount: Int,
    val reblogged: Boolean,
    val bookmarked: Boolean,
    val favourited: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val visibility: Status.Visibility,
    val attachments: List<Attachment>,
    val mentions: List<Status.Mention>,
    val tags: List<HashTag>,
    val application: Status.Application?,
    // if it has a reblogged status, it's id is stored here
    val poll: Poll?,
    val muted: Boolean,
    /** Also used as the "loading" attribute when this TimelineStatusEntity is a placeholder */
    val expanded: Boolean,
    val contentCollapsed: Boolean,
    val contentShowing: Boolean,
    val pinned: Boolean,
    val card: PreviewCard?,
    val language: String?,
    val filtered: List<FilterResult>
)
