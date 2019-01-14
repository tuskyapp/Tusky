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

import androidx.room.Entity
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Conversation
import com.keylesspalace.tusky.entity.Status

@Entity(primaryKeys = ["id","accountId"])
@TypeConverters(Converters::class)
data class ConversationEntity(
        val accountId: Long,
        val id: String,
        val accounts: List<Account>,
        val lastStatus: Status,
        val unread: Boolean
)

fun Conversation.mapToEntity(accountId: Long)=  ConversationEntity(accountId, id, accounts, lastStatus, unread)
