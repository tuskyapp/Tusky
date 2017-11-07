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
import android.os.Parcelable;

public class Card implements Parcelable {

    public String url;

    public String title;

    public String description;

    public String image;

    public String type;

    public int width;

    public int height;

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this.url == null) {
            return this == other;
        } else if (!(other instanceof Card)) {
            return false;
        }
        Card account = (Card) other;
        return account.url.equals(this.url);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(image);
        dest.writeString(type);
        dest.writeInt(width);
        dest.writeInt(height);
    }

    public Card() {}

    private Card(Parcel in) {
        url = in.readString();
        title = in.readString();
        description = in.readString();
        image = in.readString();
        type = in.readString();
        width = in.readInt();
        height = in.readInt();
    }

    public static final Creator<Card> CREATOR = new Creator<Card>() {
        @Override
        public Card createFromParcel(Parcel source) {
            return new Card(source);
        }

        @Override
        public Card[] newArray(int size) {
            return new Card[size];
        }
    };
}
