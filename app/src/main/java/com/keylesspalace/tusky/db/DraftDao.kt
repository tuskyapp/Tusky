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

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(draft: DraftEntity): Completable

    @Query("SELECT * FROM DraftEntity WHERE accountId = :accountId ORDER BY id ASC")
    fun loadDrafts(accountId: Long): DataSource.Factory<Int, DraftEntity>

    @Query("DELETE FROM DraftEntity WHERE id = :id")
    fun delete(id: Int): Completable

    @Query("SELECT * FROM DraftEntity WHERE id = :id")
    fun find(id: Int): Single<DraftEntity?>
}
