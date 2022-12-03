/* Copyright 2022 Tusky Contributors
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

import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.viewdata.StatusViewData

data class ConversationViewData(
    val id: String,
    val order: Int,
    val accounts: List<ConversationAccountEntity>,
    val unread: Boolean,
    val lastStatus: StatusViewData.Concrete
) {
    fun toEntity(
        accountId: Long,
        favourited: Boolean = lastStatus.status.favourited,
        bookmarked: Boolean = lastStatus.status.bookmarked,
        muted: Boolean = lastStatus.status.muted ?: false,
        poll: Poll? = lastStatus.status.poll,
        expanded: Boolean = lastStatus.isExpanded,
        collapsed: Boolean = lastStatus.isCollapsed,
        showingHiddenContent: Boolean = lastStatus.isShowingContent
    ): ConversationEntity {
        return ConversationEntity(
            accountId = accountId,
            id = id,
            order = order,
            accounts = accounts,
            unread = unread,
            lastStatus = lastStatus.toConversationStatusEntity(
                favourited = favourited,
                bookmarked = bookmarked,
                muted = muted,
                poll = poll,
                expanded = expanded,
                collapsed = collapsed,
                showingHiddenContent = showingHiddenContent
            )
        )
    }
}

fun StatusViewData.Concrete.toConversationStatusEntity(
    favourited: Boolean = status.favourited,
    bookmarked: Boolean = status.bookmarked,
    muted: Boolean = status.muted ?: false,
    poll: Poll? = status.poll,
    expanded: Boolean = isExpanded,
    collapsed: Boolean = isCollapsed,
    showingHiddenContent: Boolean = isShowingContent
): ConversationStatusEntity {
    return ConversationStatusEntity(
        id = id,
        url = status.url,
        inReplyToId = status.inReplyToId,
        inReplyToAccountId = status.inReplyToAccountId,
        account = status.account.toEntity(),
        content = status.content,
        createdAt = status.createdAt,
        editedAt = status.editedAt,
        emojis = status.emojis,
        favouritesCount = status.favouritesCount,
        repliesCount = status.repliesCount,
        favourited = favourited,
        bookmarked = bookmarked,
        sensitive = status.sensitive,
        spoilerText = status.spoilerText,
        attachments = status.attachments,
        mentions = status.mentions,
        tags = status.tags,
        showingHiddenContent = showingHiddenContent,
        expanded = expanded,
        collapsed = collapsed,
        muted = muted,
        poll = poll,
        language = status.language,
    )
}
