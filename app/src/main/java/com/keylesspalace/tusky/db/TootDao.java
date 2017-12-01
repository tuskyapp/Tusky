/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

/**
 * Created by cto3543 on 28/06/2017.
 *
 * DAO to fetch and update toots in the DB.
 */

@Dao
public interface TootDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(TootEntity users);

    @Query("SELECT * FROM TootEntity ORDER BY uid DESC")
    List<TootEntity> loadAll();

    @Query("DELETE FROM TootEntity WHERE uid = :uid")
    int delete(int uid);
}
