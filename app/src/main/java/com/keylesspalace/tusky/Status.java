/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Status {
    enum Visibility {
        PUBLIC,
        UNLISTED,
        PRIVATE,
    }

    private String id;
    private String accountId;
    private String displayName;
    /** the username with the remote domain appended, like @domain.name, if it's a remote account */
    private String username;
    /** the main text of the status, marked up with style for links & mentions, etc */
    private Spanned content;
    /** the fully-qualified url of the avatar image */
    private String avatar;
    private String rebloggedByDisplayName;
    /** when the status was initially created */
    private Date createdAt;
    /** whether the authenticated user has reblogged this status */
    private boolean reblogged;
    /** whether the authenticated user has favourited this status */
    private boolean favourited;
    private boolean sensitive;
    private String spoilerText;
    private Visibility visibility;
    private MediaAttachment[] attachments;
    private Mention[] mentions;

    static final int MAX_MEDIA_ATTACHMENTS = 4;

    public Status(String id, String accountId, String displayName, String username, Spanned content,
                  String avatar, Date createdAt, boolean reblogged, boolean favourited,
                  String visibility) {
        this.id = id;
        this.accountId = accountId;
        this.displayName = displayName;
        this.username = username;
        this.content = content;
        this.avatar = avatar;
        this.createdAt = createdAt;
        this.reblogged = reblogged;
        this.favourited = favourited;
        this.spoilerText = "";
        this.visibility = Visibility.valueOf(visibility.toUpperCase());
        this.attachments = new MediaAttachment[0];
        this.mentions = new Mention[0];
    }

    String getId() {
        return id;
    }

    String getAccountId() {
        return accountId;
    }

    String getDisplayName() {
        return displayName;
    }

    String getUsername() {
        return username;
    }

    Spanned getContent() {
        return content;
    }

    String getAvatar() {
        return avatar;
    }

    Date getCreatedAt() {
        return createdAt;
    }

    String getRebloggedByDisplayName() {
        return rebloggedByDisplayName;
    }

    boolean getReblogged() {
        return reblogged;
    }

    boolean getFavourited() {
        return favourited;
    }

    boolean getSensitive() {
        return sensitive;
    }

    String getSpoilerText() {
        return spoilerText;
    }

    Visibility getVisibility() {
        return visibility;
    }

    MediaAttachment[] getAttachments() {
        return attachments;
    }

    Mention[] getMentions() {
        return mentions;
    }

    private void setRebloggedByDisplayName(String name) {
        rebloggedByDisplayName = name;
    }

    void setReblogged(boolean reblogged) {
        this.reblogged = reblogged;
    }

    void setFavourited(boolean favourited) {
        this.favourited = favourited;
    }

    private void setSpoilerText(String spoilerText) {
        this.spoilerText = spoilerText;
    }

    private void setMentions(Mention[] mentions) {
        this.mentions = mentions;
    }

    private void setAttachments(MediaAttachment[] attachments, boolean sensitive) {
        this.attachments = attachments;
        this.sensitive = sensitive;
    }

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

    private static Date parseDate(String dateTime) {
        Date date;
        String s = dateTime.replace("Z", "+00:00");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            date = format.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return date;
    }

    public static Status parse(JSONObject object, boolean isReblog) throws JSONException {
        String id = object.getString("id");
        String content = object.getString("content");
        Date createdAt = parseDate(object.getString("created_at"));
        boolean reblogged = object.optBoolean("reblogged");
        boolean favourited = object.optBoolean("favourited");
        String spoilerText = object.getString("spoiler_text");
        boolean sensitive = object.optBoolean("sensitive");
        String visibility = object.getString("visibility");

        JSONObject account = object.getJSONObject("account");
        String accountId = account.getString("id");
        String displayName = account.getString("display_name");
        if (displayName.isEmpty()) {
            displayName = account.getString("username");
        }
        String username = account.getString("acct");
        String avatarUrl = account.getString("avatar");
        String avatar;
        if (!avatarUrl.equals("/avatars/original/missing.png")) {
            avatar = avatarUrl;
        } else {
            avatar = "";
        }

        JSONArray mentionsArray = object.getJSONArray("mentions");
        Mention[] mentions = null;
        if (mentionsArray != null) {
            int n = mentionsArray.length();
            mentions = new Mention[n];
            for (int i = 0; i < n; i++) {
                JSONObject mention = mentionsArray.getJSONObject(i);
                String url = mention.getString("url");
                String mentionedUsername = mention.getString("acct");
                String mentionedAccountId = mention.getString("id");
                mentions[i] = new Mention(url, mentionedUsername, mentionedAccountId);
            }
        }

        JSONArray mediaAttachments = object.getJSONArray("media_attachments");
        MediaAttachment[] attachments = null;
        if (mediaAttachments != null) {
            int n = mediaAttachments.length();
            attachments = new MediaAttachment[n];
            for (int i = 0; i < n; i++) {
                JSONObject attachment = mediaAttachments.getJSONObject(i);
                String url = attachment.getString("url");
                String previewUrl = attachment.getString("preview_url");
                String type = attachment.getString("type");
                attachments[i] = new MediaAttachment(url, previewUrl,
                        MediaAttachment.Type.valueOf(type.toUpperCase()));
            }
        }

        Status reblog = null;
        /* This case shouldn't be hit after the first recursion at all. But if this method is
         * passed unusual data this check will prevent extra recursion */
        if (!isReblog) {
            JSONObject reblogObject = object.optJSONObject("reblog");
            if (reblogObject != null) {
                reblog = parse(reblogObject, true);
            }
        }

        Status status;
        if (reblog != null) {
            status = reblog;
            status.setRebloggedByDisplayName(displayName);
        } else {
            Spanned contentPlus = HtmlUtils.fromHtml(content);
            status = new Status(
                    id, accountId, displayName, username, contentPlus, avatar, createdAt,
                    reblogged, favourited, visibility);
            if (mentions != null) {
                status.setMentions(mentions);
            }
            if (attachments != null) {
                status.setAttachments(attachments, sensitive);
            }
            if (!spoilerText.isEmpty()) {
                status.setSpoilerText(spoilerText);
            }
        }
        return status;
    }

    public static List<Status> parse(JSONArray array) throws JSONException {
        List<Status> statuses = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            statuses.add(parse(object, false));
        }
        return statuses;
    }

    static class MediaAttachment {
        enum Type {
            IMAGE,
            VIDEO,
        }

        private String url;
        private String previewUrl;
        private Type type;

        MediaAttachment(String url, String previewUrl, Type type) {
            this.url = url;
            this.previewUrl = previewUrl;
            this.type = type;
        }

        String getUrl() {
            return url;
        }

        String getPreviewUrl() {
            return previewUrl;
        }

        Type getType() {
            return type;
        }
    }

    static class Mention {
        private String url;
        private String username;
        private String id;

        Mention(String url, String username, String id) {
            this.url = url;
            this.username = username;
            this.id = id;
        }

        String getUrl() {
            return url;
        }

        String getUsername() {
            return username;
        }

        String getId() {
            return id;
        }
    }
}
