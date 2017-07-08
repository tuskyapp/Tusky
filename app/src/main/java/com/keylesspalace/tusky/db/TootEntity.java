package com.keylesspalace.tusky.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * toot model
 */

@Entity
public class TootEntity {
    @PrimaryKey(autoGenerate = true)
    private int uid;

    @ColumnInfo(name = "text")
    private String text;

    @ColumnInfo(name = "urls")
    private String urls;

    @ColumnInfo(name = "contentWarning")
    private String contentWarning;

    // getter setter
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getContentWarning() {
        return contentWarning;
    }

    public void setContentWarning(String contentWarning) {
        this.contentWarning = contentWarning;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getUrls() {
        return urls;
    }

    public void setUrls(String urls) {
        this.urls = urls;
    }
}
