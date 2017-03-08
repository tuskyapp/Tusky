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

package com.keylesspalace.tusky.entity;

import com.google.gson.annotations.SerializedName;

public class Notification {
    public enum Type {
        @SerializedName("mention")
        MENTION,
        @SerializedName("reblog")
        REBLOG,
        @SerializedName("favourite")
        FAVOURITE,
        @SerializedName("follow")
        FOLLOW,
    }

    public Type type;

    public String id;

    public Account account;

    public Status status;

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
        return notification.id.equals(this.id);
    }
}
