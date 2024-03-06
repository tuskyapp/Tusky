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

@file:OptIn(ExperimentalStdlibApi::class)

package com.keylesspalace.tusky.components.timeline

import android.util.Log
import com.keylesspalace.tusky.db.TimelineAccountEntity
import com.keylesspalace.tusky.db.TimelineStatusEntity
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Card
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.util.Date

private const val TAG = "TimelineTypeMappers"

data class Placeholder(
    val id: String,
    val loading: Boolean
)

fun TimelineAccount.toEntity(accountId: Long, moshi: Moshi): TimelineAccountEntity {
    return TimelineAccountEntity(
        serverId = id,
        timelineUserId = accountId,
        localUsername = localUsername,
        username = username,
        displayName = name,
        url = url,
        avatar = avatar,
        emojis = moshi.adapter<List<Emoji>>().toJson(emojis),
        bot = bot
    )
}

fun TimelineAccountEntity.toAccount(moshi: Moshi): TimelineAccount {
    return TimelineAccount(
        id = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        note = "",
        url = url,
        avatar = avatar,
        bot = bot,
        emojis = moshi.adapter<List<Emoji>?>().fromJson(emojis).orEmpty()
    )
}

fun Placeholder.toEntity(timelineUserId: Long): TimelineStatusEntity {
    return TimelineStatusEntity(
        serverId = this.id,
        url = null,
        timelineUserId = timelineUserId,
        authorServerId = null,
        inReplyToId = null,
        inReplyToAccountId = null,
        content = null,
        createdAt = 0L,
        editedAt = 0L,
        emojis = null,
        reblogsCount = 0,
        favouritesCount = 0,
        reblogged = false,
        favourited = false,
        bookmarked = false,
        sensitive = false,
        spoilerText = "",
        visibility = Status.Visibility.UNKNOWN,
        attachments = null,
        mentions = null,
        tags = null,
        application = null,
        reblogServerId = null,
        reblogAccountId = null,
        poll = null,
        muted = false,
        expanded = loading,
        contentCollapsed = false,
        contentShowing = false,
        pinned = false,
        card = null,
        repliesCount = 0,
        language = null,
        filtered = emptyList()
    )
}

fun Status.toEntity(
    timelineUserId: Long,
    moshi: Moshi,
    expanded: Boolean,
    contentShowing: Boolean,
    contentCollapsed: Boolean
): TimelineStatusEntity {
    return TimelineStatusEntity(
        serverId = this.id,
        url = actionableStatus.url,
        timelineUserId = timelineUserId,
        authorServerId = actionableStatus.account.id,
        inReplyToId = actionableStatus.inReplyToId,
        inReplyToAccountId = actionableStatus.inReplyToAccountId,
        content = actionableStatus.content,
        createdAt = actionableStatus.createdAt.time,
        editedAt = actionableStatus.editedAt?.time,
        emojis = actionableStatus.emojis.let { moshi.adapter<List<Emoji>>().toJson(it) },
        reblogsCount = actionableStatus.reblogsCount,
        favouritesCount = actionableStatus.favouritesCount,
        reblogged = actionableStatus.reblogged,
        favourited = actionableStatus.favourited,
        bookmarked = actionableStatus.bookmarked,
        sensitive = actionableStatus.sensitive,
        spoilerText = actionableStatus.spoilerText,
        visibility = actionableStatus.visibility,
        attachments = actionableStatus.attachments.let { moshi.adapter<List<Attachment>>().toJson(it) },
        mentions = actionableStatus.mentions.let { moshi.adapter<List<Status.Mention>>().toJson(it) },
        tags = actionableStatus.tags.let { moshi.adapter<List<HashTag>?>().toJson(it) },
        application = actionableStatus.application.let { moshi.adapter<Status.Application?>().toJson(it) },
        reblogServerId = reblog?.id,
        reblogAccountId = reblog?.let { this.account.id },
        poll = actionableStatus.poll.let { moshi.adapter<Poll?>().toJson(it) },
        muted = actionableStatus.muted,
        expanded = expanded,
        contentShowing = contentShowing,
        contentCollapsed = contentCollapsed,
        pinned = actionableStatus.pinned,
        card = actionableStatus.card?.let { moshi.adapter<Card>().toJson(it) },
        repliesCount = actionableStatus.repliesCount,
        language = actionableStatus.language,
        filtered = actionableStatus.filtered
    )
}

fun TimelineStatusWithAccount.toViewData(moshi: Moshi, isDetailed: Boolean = false): StatusViewData {
    if (this.account == null) {
        Log.d(TAG, "Constructing Placeholder(${this.status.serverId}, ${this.status.expanded})")
        return StatusViewData.Placeholder(this.status.serverId, this.status.expanded)
    }

    val attachments: List<Attachment> = status.attachments?.let { moshi.adapter<List<Attachment>?>().fromJson(it) }.orEmpty()
    val mentions: List<Status.Mention> = status.mentions?.let { moshi.adapter<List<Status.Mention>?>().fromJson(it) }.orEmpty()
    val tags: List<HashTag>? = status.tags?.let { moshi.adapter<List<HashTag>?>().fromJson(it) }
    val application = status.application?.let { moshi.adapter<Status.Application?>().fromJson(it) }
    val emojis: List<Emoji> = status.emojis?.let { moshi.adapter<List<Emoji>?>().fromJson(it) }.orEmpty()
    val poll: Poll? = status.poll?.let { moshi.adapter<Poll?>().fromJson(it) }
    val card: Card? = status.card?.let { moshi.adapter<Card?>().fromJson(it) }

    val reblog = status.reblogServerId?.let { id ->
        Status(
            id = id,
            url = status.url,
            account = account.toAccount(moshi),
            inReplyToId = status.inReplyToId,
            inReplyToAccountId = status.inReplyToAccountId,
            reblog = null,
            content = status.content.orEmpty(),
            createdAt = Date(status.createdAt),
            editedAt = status.editedAt?.let { Date(it) },
            emojis = emojis,
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            reblogged = status.reblogged,
            favourited = status.favourited,
            bookmarked = status.bookmarked,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            visibility = status.visibility,
            attachments = attachments,
            mentions = mentions,
            tags = tags,
            application = application,
            pinned = false,
            muted = status.muted,
            poll = poll,
            card = card,
            repliesCount = status.repliesCount,
            language = status.language,
            filtered = status.filtered.orEmpty()
        )
    }
    val status = if (reblog != null) {
        Status(
            id = status.serverId,
            // no url for reblogs
            url = null,
            account = this.reblogAccount!!.toAccount(moshi),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = reblog,
            content = "",
            // lie but whatever?
            createdAt = Date(status.createdAt),
            editedAt = null,
            emojis = emptyList(),
            reblogsCount = 0,
            favouritesCount = 0,
            reblogged = false,
            favourited = false,
            bookmarked = false,
            sensitive = false,
            spoilerText = "",
            visibility = status.visibility,
            attachments = emptyList(),
            mentions = emptyList(),
            tags = emptyList(),
            application = null,
            pinned = status.pinned,
            muted = status.muted,
            poll = null,
            card = null,
            repliesCount = status.repliesCount,
            language = status.language,
            filtered = status.filtered.orEmpty()
        )
    } else {
        Status(
            id = status.serverId,
            url = status.url,
            account = account.toAccount(moshi),
            inReplyToId = status.inReplyToId,
            inReplyToAccountId = status.inReplyToAccountId,
            reblog = null,
            content = status.content.orEmpty(),
            createdAt = Date(status.createdAt),
            editedAt = status.editedAt?.let { Date(it) },
            emojis = emojis,
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            reblogged = status.reblogged,
            favourited = status.favourited,
            bookmarked = status.bookmarked,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            visibility = status.visibility,
            attachments = attachments,
            mentions = mentions,
            tags = tags,
            application = application,
            pinned = status.pinned,
            muted = status.muted,
            poll = poll,
            card = card,
            repliesCount = status.repliesCount,
            language = status.language,
            filtered = status.filtered.orEmpty()
        )
    }
    return StatusViewData.Concrete(
        status = status,
        isExpanded = this.status.expanded,
        isShowingContent = this.status.contentShowing,
        isCollapsed = this.status.contentCollapsed,
        isDetailed = isDetailed
    )
}
