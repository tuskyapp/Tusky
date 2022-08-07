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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface InstanceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE, entity = InstanceEntity::class)
    suspend fun insertOrIgnore(instance: InstanceInfoEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE, entity = InstanceEntity::class)
    suspend fun updateOrIgnore(instance: InstanceInfoEntity)

    @Transaction
    suspend fun upsert(instance: InstanceInfoEntity) {
        if (insertOrIgnore(instance) == -1L) {
            updateOrIgnore(instance)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE, entity = InstanceEntity::class)
    suspend fun insertOrIgnore(emojis: EmojisEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE, entity = InstanceEntity::class)
    suspend fun updateOrIgnore(emojis: EmojisEntity)

    @Transaction
    suspend fun upsert(emojis: EmojisEntity) {
        if (insertOrIgnore(emojis) == -1L) {
            updateOrIgnore(emojis)
        }
    }

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM InstanceEntity WHERE instance = :instance LIMIT 1")
    suspend fun getInstanceInfo(instance: String): InstanceInfoEntity?

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM InstanceEntity WHERE instance = :instance LIMIT 1")
    suspend fun getEmojiInfo(instance: String): EmojisEntity?
}
