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

package com.keylesspalace.tusky.components.notifications

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keylesspalace.tusky.db.NotificationAccountEntity
import com.keylesspalace.tusky.db.NotificationDataEntity
import com.keylesspalace.tusky.db.NotificationEntity
import com.keylesspalace.tusky.db.NotificationReportEntity
import com.keylesspalace.tusky.db.NotificationStatusEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Card
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.Date

private const val TAG = "NotificationTypeMappers"

private val attachmentArrayListType = object : TypeToken<ArrayList<Attachment>>() {}.type
private val emojisListType = object : TypeToken<List<Emoji>>() {}.type
private val mentionListType = object : TypeToken<List<Status.Mention>>() {}.type
private val tagListType = object : TypeToken<List<HashTag>>() {}.type

data class Placeholder(
    val id: String,
    val loading: Boolean
)

fun TimelineAccount.toEntity(accountId: Long, gson: Gson): NotificationAccountEntity {
    return NotificationAccountEntity(
        id = id,
        tuskyAccountId = accountId,
        localUsername = localUsername,
        username = username,
        displayName = name,
        url = url,
        avatar = avatar,
        emojis = gson.toJson(emojis),
        bot = bot
    )
}

/*fun TimelineAccountEntity.toAccount(gson: Gson): TimelineAccount {
    return TimelineAccount(
        id = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        note = "",
        url = url,
        avatar = avatar,
        bot = bot,
        emojis = gson.fromJson(emojis, emojisListType)
    )
}*/

fun Placeholder.toEntity(timelineUserId: Long): NotificationEntity {
    return NotificationEntity(
        id = this.id,
        tuskyAccountId = timelineUserId,
        type = null,
        accountId = null,
        statusId = null,
        reportId = null,
        loading = loading
    )
}

fun Notification.toEntity(
    timelineUserId: Long,
    gson: Gson,
    expanded: Boolean,
    contentShowing: Boolean,
    contentCollapsed: Boolean
): NotificationEntity {
    return NotificationEntity(
        tuskyAccountId = timelineUserId,
        type = type,
        id = id,
        accountId = account.id,
        statusId = status?.id,
        reportId = report?.id,
        loading = false
    )
}

fun Report.toEntity(
    tuskyAccountId: Long,
    gson: Gson
): NotificationReportEntity {
    return NotificationReportEntity(
        tuskyAccountId = tuskyAccountId,
        id = id,
        category = category,
        statusIds = statusIds,
        createdAt = createdAt,
        targetAccountId = targetAccount.id
    )
}

fun Status.toEntity(
    tuskyAccountId: Long,
    gson: Gson,
    expanded: Boolean,
    contentShowing: Boolean,
    contentCollapsed: Boolean
): NotificationStatusEntity {
    return NotificationStatusEntity(
        id = id,
        url = url,
        tuskyAccountId = tuskyAccountId,
        authorServerId = account.id,
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        content = content,
        createdAt = createdAt.time,
        editedAt = editedAt?.time,
        emojis = gson.toJson(emojis),
        reblogsCount = reblogsCount,
        favouritesCount = favouritesCount,
        repliesCount = repliesCount,
        reblogged = reblogged,
        bookmarked = bookmarked,
        favourited = favourited,
        sensitive = sensitive,
        spoilerText = spoilerText,
        visibility = visibility,
        attachments = gson.toJson(attachments),
        mentions = gson.toJson(mentions),
        tags = gson.toJson(tags),
        application = gson.toJson(application),
        reblogServerId = null, // if it has a reblogged status, it's id is stored here
        reblogAccountId = null,
        poll = gson.toJson(poll),
        muted = muted,
        expanded = expanded,
        contentCollapsed = contentCollapsed,
        contentShowing = contentShowing,
        pinned = pinned ?: false,
        card = gson.toJson(card),
        language = language,
        filtered = emptyList()
    )
}

fun NotificationDataEntity.toViewData(gson: Gson): NotificationViewData {
    if (type == null) {
        return NotificationViewData.Placeholder(id = id, isLoading = loading)
    }

    return NotificationViewData.Concrete(
        id = id,
        type = type,
        account = account?.toViewData(gson)!!,
        statusViewData = status?.toViewData(statusAccount!!, gson),
        report = report?.toViewData(reportTargetAccount!!, gson)
    )
}

fun NotificationAccountEntity.toViewData(gson: Gson): TimelineAccount {
    return TimelineAccount(
        id = id,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        url = url,
        avatar = avatar,
        note = "",
        bot = bot,
        emojis = gson.fromJson(emojis, emojisListType)
    )
}

fun NotificationStatusEntity.toViewData(
    account: NotificationAccountEntity,
    gson: Gson
): StatusViewData.Concrete {
    val status = Status(
        id = id,
        url = url,
        account = account.toViewData(gson),
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        reblog = null,
        content = content.orEmpty(),
        createdAt = Date(createdAt),
        editedAt = editedAt?.let { Date(it) },
        emojis = gson.fromJson(emojis, emojisListType),
        reblogsCount = reblogsCount,
        favouritesCount = favouritesCount,
        reblogged = reblogged,
        favourited = favourited,
        bookmarked = bookmarked,
        sensitive = sensitive,
        spoilerText = spoilerText,
        visibility = visibility,
        attachments = gson.fromJson(attachments, attachmentArrayListType),
        mentions = gson.fromJson(mentions, mentionListType),
        tags = gson.fromJson(tags, tagListType),
        application = gson.fromJson(application, Status.Application::class.java),
        pinned = pinned,
        muted = muted,
        poll = gson.fromJson(poll, Poll::class.java),
        card = gson.fromJson(card, Card::class.java),
        repliesCount = repliesCount,
        language = language,
        filtered = filtered
    )

    return StatusViewData.Concrete(
        status = status,
        isExpanded = expanded,
        isShowingContent = contentShowing,
        isCollapsed = contentCollapsed,
        isDetailed = false
    )
}

fun NotificationReportEntity.toViewData(account: NotificationAccountEntity, gson: Gson): Report {
    return Report(
        id = id,
        category = category,
        statusIds = statusIds,
        createdAt = createdAt,
        targetAccount = account.toViewData(gson)
    )
}
