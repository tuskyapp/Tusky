package com.keylesspalace.tusky.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;

/**
 * Media model
 */

@Entity(foreignKeys = @ForeignKey(entity = TootEntity.class,
        parentColumns = "uid",
        childColumns = "toot_id"))
public class MediaEntity {
    @ColumnInfo(name = "toot_id")
    private int toot_id;
    @PrimaryKey(autoGenerate = true)
    private int uid;
    @ColumnInfo(name = "url")
    private String url;

    // getter setter
    public int getToot_id() {
        return toot_id;
    }

    public void setToot_id(int toot_id) {
        this.toot_id = toot_id;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
