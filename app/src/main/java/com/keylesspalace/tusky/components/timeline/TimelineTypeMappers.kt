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

import com.keylesspalace.tusky.db.entity.HomeTimelineData
import com.keylesspalace.tusky.db.entity.HomeTimelineEntity
import com.keylesspalace.tusky.db.entity.TimelineAccountEntity
import com.keylesspalace.tusky.db.entity.TimelineStatusEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Card
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.util.Date

data class Placeholder(
    val id: String,
    val loading: Boolean
)

fun TimelineAccount.toEntity(tuskyAccountId: Long, moshi: Moshi): TimelineAccountEntity {
    return TimelineAccountEntity(
        serverId = id,
        tuskyAccountId = tuskyAccountId,
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

fun Placeholder.toEntity(tuskyAccountId: Long): HomeTimelineEntity {
    return HomeTimelineEntity(
        id = this.id,
        tuskyAccountId = tuskyAccountId,
        statusId = null,
        reblogAccountId = null,
        loading = this.loading
    )
}

fun Status.toEntity(
    tuskyAccountId: Long,
    moshi: Moshi,
    expanded: Boolean,
    contentShowing: Boolean,
    contentCollapsed: Boolean
): TimelineStatusEntity {
    return TimelineStatusEntity(
        serverId = id,
        url = actionableStatus.url,
        tuskyAccountId = tuskyAccountId,
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

fun TimelineStatusEntity.toStatus(
    moshi: Moshi,
    account: TimelineAccountEntity
): Status {
    val attachments: List<Attachment> = moshi.adapter<List<Attachment>>().fromJson(attachments).orEmpty()
    val mentions: List<Status.Mention> = moshi.adapter<List<Status.Mention>>().fromJson(mentions).orEmpty()
    val tags: List<HashTag> = moshi.adapter<List<HashTag>>().fromJson(tags).orEmpty()
    val application = application?.let { moshi.adapter<Status.Application?>().fromJson(it) }
    val emojis: List<Emoji> = moshi.adapter<List<Emoji>?>().fromJson(emojis).orEmpty()
    val poll: Poll? = poll?.let { moshi.adapter<Poll?>().fromJson(it) }
    val card: Card? = card?.let { moshi.adapter<Card?>().fromJson(it) }

    return Status(
        id = serverId,
        url = url,
        account = account.toAccount(moshi),
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        reblog = null,
        content = content,
        createdAt = Date(createdAt),
        editedAt = editedAt?.let { Date(it) },
        emojis = emojis,
        reblogsCount = reblogsCount,
        favouritesCount = favouritesCount,
        reblogged = reblogged,
        favourited = favourited,
        bookmarked = bookmarked,
        sensitive = sensitive,
        spoilerText = spoilerText,
        visibility = visibility,
        attachments = attachments,
        mentions = mentions,
        tags = tags,
        application = application,
        pinned = false,
        muted = muted,
        poll = poll,
        card = card,
        repliesCount = repliesCount,
        language = language,
        filtered = filtered,
    )
}

fun HomeTimelineData.toViewData(moshi: Moshi, isDetailed: Boolean = false, translation: TranslationViewData? = null): StatusViewData {
    if (this.account == null || this.status == null) {
        return StatusViewData.Placeholder(this.id, loading)
    }

    val originalStatus = status.toStatus(moshi, account)
    val status = if (reblogAccount != null) {
        Status(
            id = id,
            // no url for reblogs
            url = null,
            account = reblogAccount.toAccount(moshi),
            inReplyToId = status.inReplyToId,
            inReplyToAccountId = status.inReplyToAccountId,
            reblog = originalStatus,
            content = status.content,
            // lie but whatever?
            createdAt = Date(status.createdAt),
            editedAt = null,
            emojis = emptyList(),
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            reblogged = status.reblogged,
            favourited = status.favourited,
            bookmarked = status.bookmarked,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            visibility = status.visibility,
            attachments = emptyList(),
            mentions = emptyList(),
            tags = emptyList(),
            application = null,
            pinned = false,
            muted = status.muted,
            poll = null,
            card = null,
            repliesCount = status.repliesCount,
            language = status.language,
            filtered = status.filtered,
        )
    } else {
        originalStatus
    }

    return StatusViewData.Concrete(
        status = status,
        isExpanded = this.status.expanded,
        isShowingContent = this.status.contentShowing,
        isCollapsed = this.status.contentCollapsed,
        isDetailed = isDetailed,
        translation = translation,
    )
}
