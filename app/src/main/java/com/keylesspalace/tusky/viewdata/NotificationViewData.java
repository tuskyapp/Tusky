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
 *
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a {@link Placeholder} or a {@link Concrete}.
 * It is modelled this way because close relationship between placeholder and concrete notification
 * is fine in this case. Placeholder case is not modelled as a type of notification because
 * invariants would be violated and because it would model domain incorrectly. It is prefereable to
 * {@link com.keylesspalace.tusky.util.Either} because class hierarchy is cheaper, faster and
 * more native.
 */
public abstract class NotificationViewData {
    private NotificationViewData() {
    }

    public static  final class Concrete extends NotificationViewData {
        private final Notification.Type type;
        private final String id;
        private final Account account;
        private final StatusViewData.Concrete statusViewData;
        private final boolean isExpanded;

        public Concrete(Notification.Type type, String id, Account account,
                        StatusViewData.Concrete statusViewData, boolean isExpanded) {
            this.type = type;
            this.id = id;
            this.account = account;
            this.statusViewData = statusViewData;
            this.isExpanded = isExpanded;
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

        public StatusViewData.Concrete getStatusViewData() {
            return statusViewData;
        }

        public boolean isExpanded() {
            return isExpanded;
        }
    }

    public static final class Placeholder extends NotificationViewData {
        private final boolean isLoading;

        public Placeholder(boolean isLoading) {
            this.isLoading = isLoading;
        }

        public boolean isLoading() {
            return isLoading;
        }
    }
}
