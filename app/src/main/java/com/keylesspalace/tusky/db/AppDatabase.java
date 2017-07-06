package com.keylesspalace.tusky.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

/**
 * DB version & declare DAO
 */

@Database(entities = {TootEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TootDao tootDao();
}
