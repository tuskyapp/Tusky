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

package com.keylesspalace.tusky.viewdata;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Notification;

/**
 * Created by charlag on 12/07/2017.
 */

public final class NotificationViewData {
    private final Notification.Type type;
    private final String id;
    private final Account account;
    private final StatusViewData statusViewData;
    private final boolean placeholderLoading;

    public NotificationViewData(Notification.Type type, String id, Account account,
                                StatusViewData statusViewData, boolean placeholderLoading) {
        this.type = type;
        this.id = id;
        this.account = account;
        this.statusViewData = statusViewData;
        this.placeholderLoading = placeholderLoading;
    }

    public Notification.Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public StatusViewData getStatusViewData() {
        return statusViewData;
    }

    public boolean isPlaceholderLoading() {
        return placeholderLoading;
    }
}
