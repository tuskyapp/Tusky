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

public class Notification {
    public enum Type {
        MENTION,
        REBLOG,
        FAVOURITE,
        FOLLOW,
    }
    private Type type;
    private String id;
    private String displayName;
    /** Which of the user's statuses has been mentioned, reblogged, or favourited. */
    private Status status;

    public Notification(Type type, String id, String displayName) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
    }

    public Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public @Nullable Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean hasStatusType() {
        return type == Type.MENTION
                || type == Type.FAVOURITE
                || type == Type.REBLOG;
    }

    public static List<Notification> parse(JSONArray array) throws JSONException {
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
            Notification notification = new Notification(type, id, displayName);
            if (notification.hasStatusType()) {
                JSONObject statusObject = object.getJSONObject("status");
                Status status = Status.parse(statusObject, false);
                notification.setStatus(status);
            }
            notifications.add(notification);
        }
        return notifications;
    }
}
