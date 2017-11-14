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

package com.keylesspalace.tusky.entity;

import android.text.Spanned;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;

public class Status {
    public String url;

    @SerializedName("reblogs_count")
    public String reblogsCount;

    @SerializedName("favourites_count")
    public String favouritesCount;

    @SerializedName("in_reply_to_id")
    public String inReplyToId;

    @SerializedName("in_reply_to_account_id")
    public String inReplyToAccountId;

    public String getActionableId() {
        return reblog == null ? id : reblog.id;
    }

    public Status getActionableStatus() {
        return reblog == null ? this : reblog;
    }

    public enum Visibility {
        UNKNOWN(0),
        @SerializedName("public")
        PUBLIC(1),
        @SerializedName("unlisted")
        UNLISTED(2),
        @SerializedName("private")
        PRIVATE(3),
        @SerializedName("direct")
        DIRECT(4);

        private final int num;

        Visibility(int num) {
            this.num = num;
        }

        public int getNum() {
            return num;
        }

        public static Visibility byNum(int num) {
            switch (num) {
                case 4: return DIRECT;
                case 3: return PRIVATE;
                case 2: return UNLISTED;
                case 1: return PUBLIC;
                case 0: default: return UNKNOWN;
            }
        }

        public String serverString() {
            switch (this) {
                case PUBLIC: return "public";
                case UNLISTED: return "unlisted";
                case PRIVATE: return "private";
                case DIRECT: return "direct";
                case UNKNOWN: default: return "unknown";
            }
        }
    }

    public String id;

    public Account account;

    public Spanned content;

    public Status reblog;

    @SerializedName("created_at")
    public Date createdAt;

    public boolean reblogged;

    public boolean favourited;

    public boolean sensitive;

    public List<Emoji> emojis;

    @SerializedName("spoiler_text")
    public String spoilerText;

    public Visibility visibility;

    public Visibility getVisibility() {
        return visibility == null ? Visibility.UNLISTED : visibility;
    }

    public boolean rebloggingAllowed() {
        return visibility != null
                && visibility != Visibility.PRIVATE
                && visibility != Visibility.DIRECT
                && visibility != Visibility.UNKNOWN;
    }

    @SerializedName("media_attachments")
    public MediaAttachment[] attachments;

    public Mention[] mentions;

    public Application application;

    public static final int MAX_MEDIA_ATTACHMENTS = 4;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Status status = (Status) o;
        return id != null ? id.equals(status.id) : status.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    public static class MediaAttachment {
        @com.google.gson.annotations.JsonAdapter(MediaTypeDeserializer.class)
        public enum Type {
            @SerializedName("image")
            IMAGE,
            @SerializedName("gifv")
            GIFV,
            @SerializedName("video")
            VIDEO,
            UNKNOWN
        }

        public String url;

        @SerializedName("preview_url")
        public String previewUrl;

        @SerializedName("text_url")
        public String textUrl;

        @SerializedName("remote_url")
        public String remoteUrl;

        public Type type;

        static class MediaTypeDeserializer implements JsonDeserializer<Type> {
            @Override
            public Type deserialize(JsonElement json, java.lang.reflect.Type classOfT, JsonDeserializationContext context)
                    throws JsonParseException {
                switch(json.toString()) {
                    case "\"image\"":
                        return Type.IMAGE;
                    case "\"gifv\"":
                        return Type.GIFV;
                    case "\"video\"":
                        return Type.VIDEO;
                    default:
                        return Type.UNKNOWN;
                }
            }
        }
    }

    public static final class Mention {
        public String id;

        public String url;

        @SerializedName("acct")
        public String username;

        @SerializedName("username")
        public String localUsername;
    }

    public static class Application {
        public String name;
        public String website;
    }

    @SuppressWarnings("unused")
    public static class Emoji {
        private String shortcode;
        private String url;

        public String getShortcode() {
            return shortcode;
        }

        public String getUrl() {
            return url;
        }
    }
}
