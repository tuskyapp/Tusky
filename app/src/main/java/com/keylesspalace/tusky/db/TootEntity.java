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

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverter;
import android.arch.persistence.room.TypeConverters;
import android.support.annotation.Nullable;

import com.keylesspalace.tusky.entity.Status;

/**
 * Toot model.
 */

@Entity
@TypeConverters(TootEntity.Converters.class)
public class TootEntity {
    @PrimaryKey(autoGenerate = true)
    private final int uid;

    @ColumnInfo(name = "text")
    private final String text;

    @ColumnInfo(name = "urls")
    private final String urls;

    @ColumnInfo(name = "contentWarning")
    private final String contentWarning;

    @ColumnInfo(name = "inReplyToId")
    private final String inReplyToId;

    @Nullable
    @ColumnInfo(name = "inReplyToText")
    private final String inReplyToText;

    @Nullable
    @ColumnInfo(name = "inReplyToUsername")
    private final String inReplyToUsername;

    @ColumnInfo(name = "visibility")
    private final Status.Visibility visibility;

    public TootEntity(int uid, String text, String urls, String contentWarning, String inReplyToId,
                      @Nullable String inReplyToText, @Nullable String inReplyToUsername,
                      Status.Visibility visibility) {
        this.uid = uid;
        this.text = text;
        this.urls = urls;
        this.contentWarning = contentWarning;
        this.inReplyToId = inReplyToId;
        this.inReplyToText = inReplyToText;
        this.inReplyToUsername = inReplyToUsername;
        this.visibility = visibility;
    }

    public String getText() {
        return text;
    }

    public String getContentWarning() {
        return contentWarning;
    }

    public int getUid() {
        return uid;
    }

    public String getUrls() {
        return urls;
    }

    public String getInReplyToId() {
        return inReplyToId;
    }

    @Nullable
    public String getInReplyToText() {
        return inReplyToText;
    }

    @Nullable
    public String getInReplyToUsername() {
        return inReplyToUsername;
    }

    public Status.Visibility getVisibility() {
        return visibility;
    }

    public static final class Converters {

        @TypeConverter
        public Status.Visibility visibilityFromInt(int number) {
            return Status.Visibility.byNum(number);
        }

        @TypeConverter
        public int intToVisibility(Status.Visibility visibility) {
            return visibility.getNum();
        }
    }
}
