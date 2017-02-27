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

import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class Notification {
    enum Type {
        MENTION,
        REBLOG,
        FAVOURITE,
        FOLLOW,
    }
    private Type type;
    private String id;
    private String displayName;
    private String username;
    private String avatar;
    private String accountId;
    /** Which of the user's statuses has been mentioned, reblogged, or favourited. */
    private Status status;

    private Notification(Type type, String id, String displayName, String username, String avatar,
            String accountId) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.avatar = avatar;
        this.accountId = accountId;
    }

    Type getType() {
        return type;
    }

    String getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    String getUsername() {
        return username;
    }

    String getAvatar() {
        return avatar;
    }

    String getAccountId() {
        return accountId;
    }

    @Nullable Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    private boolean hasStatusType() {
        return type == Type.MENTION
                || type == Type.FAVOURITE
                || type == Type.REBLOG;
    }

    static List<Notification> parse(JSONArray array) throws JSONException {
        List<Notification> notifications = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            String id = object.getString("id");
            Notification.Type type = Notification.Type.valueOf(
                    object.getString("type").toUpperCase());
            JSONObject account = object.getJSONObject("account");
            String displayName = account.getString("display_name");
            if (displayName.isEmpty()) {
                displayName = account.getString("username");
            }
            String username = account.getString("acct");
            String avatar = account.getString("avatar");
            String accountId = account.getString("id");
            Notification notification = new Notification(type, id, displayName, username, avatar,
                    accountId);
            if (notification.hasStatusType()) {
                JSONObject statusObject = object.getJSONObject("status");
                Status status = Status.parse(statusObject, false);
                notification.setStatus(status);
            }
            notifications.add(notification);
        }
        return notifications;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this.id == null) {
            return this == other;
        } else if (!(other instanceof Notification)) {
            return false;
        }
        Notification notification = (Notification) other;
        return notification.getId().equals(this.id);
    }
}
