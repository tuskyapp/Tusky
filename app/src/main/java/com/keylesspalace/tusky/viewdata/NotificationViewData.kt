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

import androidx.annotation.Nullable;

import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Report;
import com.keylesspalace.tusky.entity.TimelineAccount;

import java.util.Objects;

/**
 * Created by charlag on 12/07/2017.
 * <p>
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a {@link Placeholder} or a {@link Concrete}.
 * It is modelled this way because close relationship between placeholder and concrete notification
 * is fine in this case. Placeholder case is not modelled as a type of notification because
 * invariants would be violated and because it would model domain incorrectly. It is preferable to
 * {@link com.keylesspalace.tusky.util.Either} because class hierarchy is cheaper, faster and
 * more native.
 */
public abstract class NotificationViewData {
    private NotificationViewData() {
    }

    public abstract long getViewDataId();

    public abstract boolean deepEquals(NotificationViewData other);

    public static final class Concrete extends NotificationViewData {
        private final Notification.Type type;
        private final String id;
        private final TimelineAccount account;
        @Nullable
        private final StatusViewData.Concrete statusViewData;
        @Nullable
        private final Report report;

        public Concrete(Notification.Type type, String id, TimelineAccount account,
                        @Nullable StatusViewData.Concrete statusViewData, @Nullable Report report) {
            this.type = type;
            this.id = id;
            this.account = account;
            this.statusViewData = statusViewData;
            this.report = report;
        }

        public Notification.Type getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public TimelineAccount getAccount() {
            return account;
        }

        @Nullable
        public StatusViewData.Concrete getStatusViewData() {
            return statusViewData;
        }

        @Nullable
        public Report getReport() {
            return report;
        }

        @Override
        public long getViewDataId() {
            return id.hashCode();
        }

        @Override
        public boolean deepEquals(NotificationViewData o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Concrete concrete = (Concrete) o;
            return type == concrete.type &&
                    Objects.equals(id, concrete.id) &&
                    account.getId().equals(concrete.account.getId()) &&
                    (Objects.equals(statusViewData, concrete.statusViewData)) &&
                    (Objects.equals(report, concrete.report));
        }

        @Override
        public int hashCode() {

            return Objects.hash(type, id, account, statusViewData);
        }

        public Concrete copyWithStatus(@Nullable StatusViewData.Concrete statusViewData) {
            return new Concrete(type, id, account, statusViewData, report);
        }
    }

    public static final class Placeholder extends NotificationViewData {
        private final long id;
        private final boolean isLoading;

        public Placeholder(long id, boolean isLoading) {
            this.id = id;
            this.isLoading = isLoading;
        }

        public boolean isLoading() {
            return isLoading;
        }

        @Override
        public long getViewDataId() {
            return id;
        }

        @Override
        public boolean deepEquals(NotificationViewData other) {
            if (!(other instanceof Placeholder)) return false;
            Placeholder that = (Placeholder) other;
            return isLoading == that.isLoading && id == that.id;
        }
    }
}
