package com.keylesspalace.tusky.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

/**
 * Created by cto3543 on 28/06/2017.
 * crud interface on this Toot DB
 */

@Dao
public interface TootDao {
    // c
    @Insert
    long insert(TootEntity users);

    // r
    @Query("SELECT * FROM TootEntity")
    List<TootEntity> loadAll();

    @Query("SELECT * FROM TootEntity WHERE uid IN (:uid)")
    List<TootEntity> loadAllByTootId(int... uid);

    // u
    @Update
    void updateToot(TootEntity... toot);

    // d
    @Delete
    void delete(TootEntity user);
}
