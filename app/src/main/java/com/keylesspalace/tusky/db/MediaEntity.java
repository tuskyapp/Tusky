package com.keylesspalace.tusky.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by cto3543 on 28/06/2017.
 */

@Entity
public class MediaEntity {
    @PrimaryKey
    private int uid;

    @ColumnInfo(name = "url")
    private String text;
}
