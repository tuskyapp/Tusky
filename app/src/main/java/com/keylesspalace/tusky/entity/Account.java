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

import android.os.Parcel;
import android.text.Spanned;

import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.google.gson.annotations.SerializedName;

import org.parceler.Parcels;

public class Account {
    public String id;

    @SerializedName("acct")
    public String username;

    @SerializedName("display_name")
    public String displayName;

    public Spanned note;

    public String url;

    public String avatar;

    public String header;

    public boolean locked;

    @SerializedName("followers_count")
    public String followersCount;

    @SerializedName("following_count")
    public String followingCount;

    @SerializedName("statuses_count")
    public String statusesCount;

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this.id == null) {
            return this == other;
        } else if (!(other instanceof Account)) {
            return false;
        }
        Account account = (Account) other;
        return account.id.equals(this.id);
    }
}
