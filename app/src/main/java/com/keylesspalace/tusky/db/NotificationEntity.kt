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
import androidx.room.TypeConverters
import com.keylesspalace.tusky.entity.FilterResult
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import java.util.Date

data class NotificationDataEntity(
    val tuskyAccountId: Long, // id of the account logged into Tusky this notifications belongs to
    val type: Notification.Type?, // null when placeholder
    val id: String,
    @Embedded(prefix = "a_") val account: NotificationAccountEntity?,
    @Embedded(prefix = "s_") val status: NotificationStatusEntity?,
    @Embedded(prefix = "sa_") val statusAccount: NotificationAccountEntity?,
    @Embedded(prefix = "r_") val report: NotificationReportEntity?,
    @Embedded(prefix = "ra_") val reportTargetAccount: NotificationAccountEntity?,
    val loading: Boolean = false // relevant when it is a placeholder
)

@Entity(
    primaryKeys = ["id", "tuskyAccountId"]
)
@TypeConverters(Converters::class)
data class NotificationEntity(
    val tuskyAccountId: Long, // id of the account logged into Tusky this notifications belongs to
    val type: Notification.Type?, // null when placeholder
    val id: String,
    val accountId: String?,
    val statusId: String?,
    val reportId: String?,
    val loading: Boolean = false // relevant when it is a placeholder
)

@Entity(
    primaryKeys = ["id", "tuskyAccountId"]
)
@TypeConverters(Converters::class)
data class NotificationReportEntity(
    val tuskyAccountId: Long, // id of the account logged into Tusky this report belongs to
    val id: String,
    val category: String,
    val statusIds: List<String>?,
    val createdAt: Date,
    val targetAccountId: String?
)

@Entity(
    primaryKeys = ["id", "tuskyAccountId"]
)
@TypeConverters(Converters::class)
data class NotificationStatusEntity(
    val id: String,
    val url: String?,
    val tuskyAccountId: Long,
    val authorServerId: String?,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val content: String?,
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
    val reblogServerId: String?, // if it has a reblogged status, it's id is stored here
    val reblogAccountId: String?,
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
    primaryKeys = ["id", "tuskyAccountId"]
)
data class NotificationAccountEntity(
    val id: String,
    val tuskyAccountId: Long,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val url: String,
    val avatar: String,
    val emojis: String,
    val bot: Boolean
)
