package com.keylesspalace.tusky.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

/**
 * Created by cto3543 on 28/06/2017.
 */

@Database(entities = {TootEntity.class}, version = 2, exportSchema = false)
abstract public class AppDatabase extends RoomDatabase {
    public abstract TootDao tootDao();
}
