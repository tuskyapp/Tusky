package com.keylesspalace.tusky.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

/**
 * Created by cto3543 on 28/06/2017.
 */

@Dao
public interface TootDao {
    @Query("SELECT * FROM TootEntity")
    List<TootEntity> loadAll();

    @Query("SELECT * FROM TootEntity WHERE uid IN (:uid)")
    List<TootEntity> loadAllByUserId(int... uid);

    @Insert
    long insert(TootEntity users);

    @Insert
    void insertAll(TootEntity... users);

    @Delete
    void delete(TootEntity user);
}
