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

package com.keylesspalace.tusky.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import com.keylesspalace.tusky.db.entity.EmojisEntity
import com.keylesspalace.tusky.db.entity.InstanceEntity
import com.keylesspalace.tusky.db.entity.InstanceInfoEntity

@Dao
interface InstanceDao {

    @Upsert(entity = InstanceEntity::class)
    suspend fun upsert(instance: InstanceInfoEntity)

    @Upsert(entity = InstanceEntity::class)
    suspend fun upsert(emojis: EmojisEntity)

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM InstanceEntity WHERE instance = :instance LIMIT 1")
    suspend fun getInstanceInfo(instance: String): InstanceInfoEntity?

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM InstanceEntity WHERE instance = :instance LIMIT 1")
    suspend fun getEmojiInfo(instance: String): EmojisEntity?

    @Query("UPDATE InstanceEntity SET filterV2Supported = :filterV2Support WHERE instance = :instance")
    suspend fun setFilterV2Support(instance: String, filterV2Support: Boolean)

    @Query("SELECT filterV2Supported FROM InstanceEntity WHERE instance = :instance LIMIT 1")
    suspend fun getFilterV2Support(instance: String): Boolean
}
