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
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import java.util.Date

data class Placeholder(
    val id: String,
    val loading: Boolean
)

fun TimelineAccount.toEntity(tuskyAccountId: Long): TimelineAccountEntity {
    return TimelineAccountEntity(
        serverId = id,
        tuskyAccountId = tuskyAccountId,
        localUsername = localUsername,
        username = username,
        displayName = name,
        url = url,
        avatar = avatar,
        emojis = emojis,
        bot = bot
    )
}

fun TimelineAccountEntity.toAccount(): TimelineAccount {
    return TimelineAccount(
        id = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        note = "",
        url = url,
        avatar = avatar,
        bot = bot,
        emojis = emojis
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
    expanded: Boolean,
    contentShowing: Boolean,
    contentCollapsed: Boolean
) = TimelineStatusEntity(
    serverId = id,
    url = actionableStatus.url,
    tuskyAccountId = tuskyAccountId,
    authorServerId = actionableStatus.account.id,
    inReplyToId = actionableStatus.inReplyToId,
    inReplyToAccountId = actionableStatus.inReplyToAccountId,
    content = actionableStatus.content,
    createdAt = actionableStatus.createdAt.time,
    editedAt = actionableStatus.editedAt?.time,
    emojis = actionableStatus.emojis,
    reblogsCount = actionableStatus.reblogsCount,
    favouritesCount = actionableStatus.favouritesCount,
    reblogged = actionableStatus.reblogged,
    favourited = actionableStatus.favourited,
    bookmarked = actionableStatus.bookmarked,
    sensitive = actionableStatus.sensitive,
    spoilerText = actionableStatus.spoilerText,
    visibility = actionableStatus.visibility,
    attachments = actionableStatus.attachments,
    mentions = actionableStatus.mentions,
    tags = actionableStatus.tags,
    application = actionableStatus.application,
    poll = actionableStatus.poll,
    muted = actionableStatus.muted,
    expanded = expanded,
    contentShowing = contentShowing,
    contentCollapsed = contentCollapsed,
    pinned = actionableStatus.pinned,
    card = actionableStatus.card,
    repliesCount = actionableStatus.repliesCount,
    language = actionableStatus.language,
    filtered = actionableStatus.filtered
)

fun TimelineStatusEntity.toStatus(
    account: TimelineAccountEntity
) = Status(
    id = serverId,
    url = url,
    account = account.toAccount(),
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

fun HomeTimelineData.toViewData(isDetailed: Boolean = false, translation: TranslationViewData? = null): StatusViewData {
    if (this.account == null || this.status == null) {
        return StatusViewData.Placeholder(this.id, loading)
    }

    val originalStatus = status.toStatus(account)
    val status = if (reblogAccount != null) {
        Status(
            id = id,
            // no url for reblogs
            url = null,
            account = reblogAccount.toAccount(),
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
