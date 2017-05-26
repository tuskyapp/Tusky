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

import android.os.Parcel;
import android.text.Spanned;

import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.google.gson.annotations.SerializedName;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.json.StringWithEmoji;

public class Account implements SearchSuggestion {
    public String id;

    @SerializedName("username")
    public String localUsername;

    @SerializedName("acct")
    public String username;

    @SerializedName("display_name")
    public StringWithEmoji displayName;

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

    public String getDisplayName() {
        if (displayName.value.length() == 0) {
            return localUsername;
        }
        return displayName.value;
    }

    @Override
    public String getBody() {
        return username;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(localUsername);
        dest.writeString(username);
        dest.writeString(displayName.value);
        dest.writeString(HtmlUtils.toHtml(note));
        dest.writeString(url);
        dest.writeString(avatar);
        dest.writeString(header);
        dest.writeBooleanArray(new boolean[] { locked });
        dest.writeString(followersCount);
        dest.writeString(followingCount);
        dest.writeString(statusesCount);
    }

    public Account() {

    }

    protected Account(Parcel in) {
        id = in.readString();
        localUsername = in.readString();
        username = in.readString();
        displayName = new StringWithEmoji(in.readString());
        note = HtmlUtils.fromHtml(in.readString());
        url = in.readString();
        avatar = in.readString();
        header = in.readString();
        boolean[] lockedArray = new boolean[1];
        in.readBooleanArray(lockedArray);
        locked = lockedArray[0];
        followersCount = in.readString();
        followingCount = in.readString();
        statusesCount = in.readString();
    }

    public static final Creator<Account> CREATOR = new Creator<Account>() {
        @Override
        public Account createFromParcel(Parcel source) {
            return new Account(source);
        }

        @Override
        public Account[] newArray(int size) {
            return new Account[size];
        }
    };
}
