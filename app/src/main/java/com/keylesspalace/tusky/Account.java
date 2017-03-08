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

import java.util.ArrayList;
import java.util.List;

class Account {
    String id;
    String username;
    String displayName;
    Spanned note;
    String url;
    String avatar;
    String header;
    String followersCount;
    String followingCount;
    String statusesCount;

    public static Account parse(JSONObject object) throws JSONException {
        Account account = new Account();
        account.id = object.getString("id");
        account.username = object.getString("acct");
        account.displayName = object.getString("display_name");
        if (account.displayName.isEmpty()) {
            account.displayName = object.getString("username");
        }
        account.note = HtmlUtils.fromHtml(object.getString("note"));
        account.url = object.getString("url");
        String avatarUrl = object.getString("avatar");
        if (!avatarUrl.equals("/avatars/original/missing.png")) {
            account.avatar = avatarUrl;
        } else {
            account.avatar = null;
        }
        String headerUrl = object.getString("header");
        if (!headerUrl.equals("/headers/original/missing.png")) {
            account.header = headerUrl;
        } else {
            account.header = null;
        }
        account.followersCount = object.getString("followers_count");
        account.followingCount = object.getString("following_count");
        account.statusesCount = object.getString("statuses_count");
        return account;
    }

    public static List<Account> parse(JSONArray array) throws JSONException {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            Account account = parse(object);
            accounts.add(account);
        }
        return accounts;
    }

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
