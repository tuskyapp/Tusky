/* Copyright 2019 Conny Duck
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

import android.text.Spanned
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.entity.*
import com.keylesspalace.tusky.util.SmartLengthInputFilter
import java.util.*

@Entity(primaryKeys = ["id","accountId"])
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
)

@TypeConverters(Converters::class)
data class ConversationStatusEntity(
        val id: String?,
        val inReplyToId: String?,
        val inReplyToAccountId: String?,
        val account: ConversationAccountEntity,
        val content: Spanned,
        val createdAt: Date,
        val emojis: List<Emoji>,
        val favouritesCount: Int,
        val favourited: Boolean,
        val sensitive: Boolean,
        val spoilerText: String?,
        val attachments: List<Attachment>,
        val mentions: Array<Status.Mention>,
        val expanded: Boolean,
        val collapsible: Boolean,
        val collapsed: Boolean
)


fun Account.toEntity() =
        ConversationAccountEntity(
                id,
                username,
                displayName,
                avatar,
                emojis ?: emptyList()
        )

fun Status.toEntity() =
        ConversationStatusEntity(
                id, inReplyToId, inReplyToAccountId, account.toEntity(), content, createdAt,
                emojis, favouritesCount, favourited, sensitive,
                spoilerText, attachments, mentions,
                false,
                !SmartLengthInputFilter.hasBadRatio(content, SmartLengthInputFilter.LENGTH_DEFAULT),
                true
        )


fun Conversation.toEntity(accountId: Long) =
        ConversationEntity(
                accountId,
                id,
                accounts.map { it.toEntity() },
                unread,
                lastStatus.toEntity()
        )
