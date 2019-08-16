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

import com.google.gson.Gson;
import com.keylesspalace.tusky.entity.NewPoll;
import com.keylesspalace.tusky.entity.Status;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

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

    @ColumnInfo(name = "descriptions")
    private final String descriptions;

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

    @Nullable
    @ColumnInfo(name = "poll")
    private final NewPoll poll;

    public TootEntity(int uid, String text, String urls, String descriptions, String contentWarning, String inReplyToId,
                      @Nullable String inReplyToText, @Nullable String inReplyToUsername,
                      Status.Visibility visibility, @Nullable NewPoll poll) {
        this.uid = uid;
        this.text = text;
        this.urls = urls;
        this.descriptions = descriptions;
        this.contentWarning = contentWarning;
        this.inReplyToId = inReplyToId;
        this.inReplyToText = inReplyToText;
        this.inReplyToUsername = inReplyToUsername;
        this.visibility = visibility;
        this.poll = poll;
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

    public String getDescriptions() {
        return descriptions;
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

    @Nullable
    public NewPoll getPoll() {
        return poll;
    }

    public static final class Converters {

        private static final Gson gson = new Gson();

        @TypeConverter
        public Status.Visibility visibilityFromInt(int number) {
            return Status.Visibility.byNum(number);
        }

        @TypeConverter
        public int intFromVisibility(Status.Visibility visibility) {
            return visibility == null ? Status.Visibility.UNKNOWN.getNum() : visibility.getNum();
        }

        @TypeConverter
        public String pollToString(NewPoll poll) {
            return gson.toJson(poll);
        }

        @TypeConverter
        public NewPoll stringToPoll(String poll) {
            return gson.fromJson(poll, NewPoll.class);
        }
    }
}
