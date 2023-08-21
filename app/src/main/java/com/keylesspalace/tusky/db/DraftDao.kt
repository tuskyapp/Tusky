/* Copyright 2020 Tusky Contributors
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

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(draft: DraftEntity)

    @Query("SELECT * FROM DraftEntity WHERE accountId = :accountId ORDER BY id ASC")
    fun draftsPagingSource(accountId: Long): PagingSource<Int, DraftEntity>

    @Query("SELECT COUNT(*) FROM DraftEntity WHERE accountId = :accountId AND failedToSendNew = 1")
    fun draftsNeedUserAlert(accountId: Long): LiveData<Int>

    @Query("UPDATE DraftEntity SET failedToSendNew = 0 WHERE accountId = :accountId AND failedToSendNew = 1")
    suspend fun draftsClearNeedUserAlert(accountId: Long)

    @Query("SELECT * FROM DraftEntity WHERE accountId = :accountId")
    suspend fun loadDrafts(accountId: Long): List<DraftEntity>

    @Query("DELETE FROM DraftEntity WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM DraftEntity WHERE id = :id")
    suspend fun find(id: Int): DraftEntity?
}
