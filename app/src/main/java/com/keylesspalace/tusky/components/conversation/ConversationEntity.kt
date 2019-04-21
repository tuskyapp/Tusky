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
import android.text.SpannedString
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
) {
    fun toAccount(): Account {
        return Account(
                id = id,
                username = username,
                displayName = displayName,
                avatar = avatar,
                emojis = emojis,
                url = "",
                localUsername = "",
                note = SpannedString(""),
                header = ""
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
        val content: Spanned,
        val createdAt: Date,
        val emojis: List<Emoji>,
        val favouritesCount: Int,
        val favourited: Boolean,
        val sensitive: Boolean,
        val spoilerText: String,
        val attachments: ArrayList<Attachment>,
        val mentions: Array<Status.Mention>,
        val showingHiddenContent: Boolean,
        val expanded: Boolean,
        val collapsible: Boolean,
        val collapsed: Boolean

) {
    /** its necessary to override this because Spanned.equals does not work as expected  */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversationStatusEntity

        if (id != other.id) return false
        if (url != other.url) return false
        if (inReplyToId != other.inReplyToId) return false
        if (inReplyToAccountId != other.inReplyToAccountId) return false
        if (account != other.account) return false
        if (content.toString() != other.content.toString()) return false //TODO find a better method to compare two spanned strings
        if (createdAt != other.createdAt) return false
        if (emojis != other.emojis) return false
        if (favouritesCount != other.favouritesCount) return false
        if (favourited != other.favourited) return false
        if (sensitive != other.sensitive) return false
        if (spoilerText != other.spoilerText) return false
        if (attachments != other.attachments) return false
        if (!mentions.contentEquals(other.mentions)) return false
        if (showingHiddenContent != other.showingHiddenContent) return false
        if (expanded != other.expanded) return false
        if (collapsible != other.collapsible) return false
        if (collapsed != other.collapsed) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (inReplyToId?.hashCode() ?: 0)
        result = 31 * result + (inReplyToAccountId?.hashCode() ?: 0)
        result = 31 * result + account.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + emojis.hashCode()
        result = 31 * result + favouritesCount
        result = 31 * result + favourited.hashCode()
        result = 31 * result + sensitive.hashCode()
        result = 31 * result + spoilerText.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + mentions.contentHashCode()
        result = 31 * result + showingHiddenContent.hashCode()
        result = 31 * result + expanded.hashCode()
        result = 31 * result + collapsible.hashCode()
        result = 31 * result + collapsed.hashCode()
        return result
    }

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
                sensitive= sensitive,
                spoilerText = spoilerText,
                visibility = Status.Visibility.DIRECT,
                attachments = attachments,
                mentions = mentions,
                application = null,
                pinned = false)
    }
}

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
                id, url, inReplyToId, inReplyToAccountId, account.toEntity(), content,
                createdAt, emojis, favouritesCount, favourited, sensitive,
                spoilerText, attachments, mentions,
                false,
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
                lastStatus!!.toEntity()
        )
