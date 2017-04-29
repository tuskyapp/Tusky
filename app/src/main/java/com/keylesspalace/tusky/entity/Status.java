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

import com.google.gson.annotations.SerializedName;

import java.util.Date;

public class Status {
    private Status actionableStatus;

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
        UNKNOWN,
        @SerializedName("public")
        PUBLIC,
        @SerializedName("unlisted")
        UNLISTED,
        @SerializedName("private")
        PRIVATE,
        @SerializedName("direct")
        DIRECT,
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

    public static final int MAX_MEDIA_ATTACHMENTS = 4;

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this.id == null) {
            return this == other;
        } else if (!(other instanceof Status)) {
            return false;
        }
        Status status = (Status) other;
        return status.id.equals(this.id);
    }

    public static class MediaAttachment {
        public enum Type {
            @SerializedName("image")
            IMAGE,
            @SerializedName("gifv")
            GIFV,
            @SerializedName("video")
            VIDEO,
            UNKNOWN,
        }

        public String url;

        @SerializedName("preview_url")
        public String previewUrl;

        @SerializedName("text_url")
        public String textUrl;

        @SerializedName("remote_url")
        public String remoteUrl;

        public Type type;
    }

    public static class Mention {
        public String id;

        public String url;

        @SerializedName("acct")
        public String username;

        @SerializedName("username")
        public String localUsername;
    }
}
