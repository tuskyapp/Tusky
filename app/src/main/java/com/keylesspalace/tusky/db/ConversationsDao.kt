/* Copyright 2018 Conny Duck
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

package com.keylesspalace.tusky.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keylesspalace.tusky.components.conversation.ConversationEntity

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("DELETE FROM ConversationEntity WHERE id = :id AND accountId = :accountId")
    suspend fun delete(id: String, accountId: Long)

    @Query("SELECT * FROM ConversationEntity WHERE accountId = :accountId ORDER BY `order` ASC")
    fun conversationsForAccount(accountId: Long): PagingSource<Int, ConversationEntity>

    @Query("DELETE FROM ConversationEntity WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}
