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

package com.keylesspalace.tusky.components.timeline

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.util.Date

private const val TAG = "TimelineTypeMappers"

data class Placeholder(
    val id: String,
    val loading: Boolean
)

private val attachmentArrayListType = object : TypeToken<ArrayList<Attachment>>() {}.type
private val emojisListType = object : TypeToken<List<Emoji>>() {}.type
private val mentionListType = object : TypeToken<List<Status.Mention>>() {}.type
private val tagListType = object : TypeToken<List<HashTag>>() {}.type

fun TimelineAccount.toEntity(accountId: Long, gson: Gson): TimelineAccountEntity {
    return TimelineAccountEntity(
        serverId = id,
        timelineUserId = accountId,
        localUsername = localUsername,
        username = username,
        displayName = name,
        url = url,
        avatar = avatar,
        emojis = gson.toJson(emojis),
        bot = bot
    )
}

fun TimelineAccountEntity.toAccount(gson: Gson): TimelineAccount {
    return TimelineAccount(
        id = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        url = url,
        avatar = avatar,
        bot = bot,
        emojis = gson.fromJson(emojis, emojisListType)
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
    )
}

fun Status.toEntity(
    timelineUserId: Long,
    gson: Gson,
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
        emojis = actionableStatus.emojis.let(gson::toJson),
        reblogsCount = actionableStatus.reblogsCount,
        favouritesCount = actionableStatus.favouritesCount,
        reblogged = actionableStatus.reblogged,
        favourited = actionableStatus.favourited,
        bookmarked = actionableStatus.bookmarked,
        sensitive = actionableStatus.sensitive,
        spoilerText = actionableStatus.spoilerText,
        visibility = actionableStatus.visibility,
        attachments = actionableStatus.attachments.let(gson::toJson),
        mentions = actionableStatus.mentions.let(gson::toJson),
        tags = actionableStatus.tags.let(gson::toJson),
        application = actionableStatus.application.let(gson::toJson),
        reblogServerId = reblog?.id,
        reblogAccountId = reblog?.let { this.account.id },
        poll = actionableStatus.poll.let(gson::toJson),
        muted = actionableStatus.muted,
        expanded = expanded,
        contentShowing = contentShowing,
        contentCollapsed = contentCollapsed,
        pinned = actionableStatus.pinned == true,
        card = actionableStatus.card?.let(gson::toJson),
        repliesCount = actionableStatus.repliesCount,
        language = actionableStatus.language,
    )
}

fun TimelineStatusWithAccount.toViewData(gson: Gson, isDetailed: Boolean = false): StatusViewData {
    if (this.account == null) {
        Log.d(TAG, "Constructing Placeholder(${this.status.serverId}, ${this.status.expanded})")
        return StatusViewData.Placeholder(this.status.serverId, this.status.expanded)
    }

    val attachments: ArrayList<Attachment> = gson.fromJson(status.attachments, attachmentArrayListType) ?: arrayListOf()
    val mentions: List<Status.Mention> = gson.fromJson(status.mentions, mentionListType) ?: emptyList()
    val tags: List<HashTag>? = gson.fromJson(status.tags, tagListType)
    val application = gson.fromJson(status.application, Status.Application::class.java)
    val emojis: List<Emoji> = gson.fromJson(status.emojis, emojisListType) ?: emptyList()
    val poll: Poll? = gson.fromJson(status.poll, Poll::class.java)
    val card: Card? = gson.fromJson(status.card, Card::class.java)

    val reblog = status.reblogServerId?.let { id ->
        Status(
            id = id,
            url = status.url,
            account = account.toAccount(gson),
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
        )
    }
    val status = if (reblog != null) {
        Status(
            id = status.serverId,
            url = null, // no url for reblogs
            account = this.reblogAccount!!.toAccount(gson),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = reblog,
            content = "",
            createdAt = Date(status.createdAt), // lie but whatever?
            editedAt = null,
            emojis = listOf(),
            reblogsCount = 0,
            favouritesCount = 0,
            reblogged = false,
            favourited = false,
            bookmarked = false,
            sensitive = false,
            spoilerText = "",
            visibility = status.visibility,
            attachments = ArrayList(),
            mentions = listOf(),
            tags = listOf(),
            application = null,
            pinned = status.pinned,
            muted = status.muted,
            poll = null,
            card = null,
            repliesCount = status.repliesCount,
            language = status.language,
        )
    } else {
        Status(
            id = status.serverId,
            url = status.url,
            account = account.toAccount(gson),
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
