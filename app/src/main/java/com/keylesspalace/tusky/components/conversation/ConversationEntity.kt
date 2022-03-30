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

package com.keylesspalace.tusky.components.conversation

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Conversation
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.util.shouldTrimStatus
import java.util.Date

@Entity(primaryKeys = ["id", "accountId"])
@TypeConverters(Converters::class)
data class ConversationEntity(
    val accountId: Long,
    val id: String,
    val accounts: List<ConversationAccountEntity>,
    val unread: Boolean,
    @Embedded(prefix = "s_") val lastStatus: ConversationStatusEntity
)

data class ConversationAccountEntity(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val emojis: List<Emoji>
) {
    fun toAccount(): TimelineAccount {
        return TimelineAccount(
            id = id,
            username = username,
            displayName = displayName,
            url = "",
            avatar = avatar,
            emojis = emojis,
            localUsername = "",
        )
    }
}

@TypeConverters(Converters::class)
data class ConversationStatusEntity(
    val id: String,
    val url: String?,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val account: ConversationAccountEntity,
    val content: String,
    val createdAt: Date,
    val emojis: List<Emoji>,
    val favouritesCount: Int,
    val favourited: Boolean,
    val bookmarked: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val attachments: ArrayList<Attachment>,
    val mentions: List<Status.Mention>,
    val tags: List<HashTag>?,
    val showingHiddenContent: Boolean,
    val expanded: Boolean,
    val collapsible: Boolean,
    val collapsed: Boolean,
    val muted: Boolean,
    val poll: Poll?
) {

    fun toStatus(): Status {
        return Status(
            id = id,
            url = url,
            account = account.toAccount(),
            inReplyToId = inReplyToId,
            inReplyToAccountId = inReplyToAccountId,
            content = content,
            reblog = null,
            createdAt = createdAt,
            emojis = emojis,
            reblogsCount = 0,
            favouritesCount = favouritesCount,
            reblogged = false,
            favourited = favourited,
            bookmarked = bookmarked,
            sensitive = sensitive,
            spoilerText = spoilerText,
            visibility = Status.Visibility.DIRECT,
            attachments = attachments,
            mentions = mentions,
            tags = tags,
            application = null,
            pinned = false,
            muted = muted,
            poll = poll,
            card = null
        )
    }
}

fun TimelineAccount.toEntity() =
    ConversationAccountEntity(
        id = id,
        username = username,
        displayName = name,
        avatar = avatar,
        emojis = emojis ?: emptyList()
    )

fun Status.toEntity() =
    ConversationStatusEntity(
        id = id,
        url = url,
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        account = account.toEntity(),
        content = content,
        createdAt = createdAt,
        emojis = emojis,
        favouritesCount = favouritesCount,
        favourited = favourited,
        bookmarked = bookmarked,
        sensitive = sensitive,
        spoilerText = spoilerText,
        attachments = attachments,
        mentions = mentions,
        tags = tags,
        showingHiddenContent = false,
        expanded = false,
        collapsible = false, //TODO
        collapsed = true,
        muted = muted ?: false,
        poll = poll
    )

fun Conversation.toEntity(accountId: Long) =
    ConversationEntity(
        accountId = accountId,
        id = id,
        accounts = accounts.map { it.toEntity() },
        unread = unread,
        lastStatus = lastStatus!!.toEntity()
    )
