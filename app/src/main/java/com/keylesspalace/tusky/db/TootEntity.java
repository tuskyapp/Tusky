package com.keylesspalace.tusky.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by cto3543 on 28/06/2017.
 */

@Entity
public class TootEntity {
    @PrimaryKey(autoGenerate = true)
    private int uid;

    @ColumnInfo(name = "text")
    private String text;

    // set get
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }
}
