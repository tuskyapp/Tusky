/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Tusky. If
 * not, see <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.network.MastodonApi;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Response;

/**
 * Created by charlag on 31/10/17.
 */

public final class NotificationPullJobCreator implements JobCreator {

    private static final String TAG = "NotificationPJC";

    static final String NOTIFICATIONS_JOB_TAG = "notifications_job_tag";

    private final MastodonApi api;
    private final Context context;
    private final AccountManager accountManager;

    @Inject NotificationPullJobCreator(MastodonApi api, Context context,
                                       AccountManager accountManager) {
        this.api = api;
        this.context = context;
        this.accountManager = accountManager;
    }

    @Nullable
    @Override
    public Job create(@NonNull String tag) {
        if (tag.equals(NOTIFICATIONS_JOB_TAG)) {
            return new NotificationPullJob(context, accountManager, api);
        }
        return null;
    }

    private final static class NotificationPullJob extends Job {

        private final Context context;
        private final AccountManager accountManager;
        private final MastodonApi mastodonApi;

        NotificationPullJob(Context context, AccountManager accountManager,
                            MastodonApi mastodonApi) {
            this.context = context;
            this.accountManager = accountManager;
            this.mastodonApi = mastodonApi;
        }

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            List<AccountEntity> accountList = new ArrayList<>(accountManager.getAllAccountsOrderedByActive());
            for (AccountEntity account : accountList) {
                if (account.getNotificationsEnabled()) {
                    try {
                        Log.d(TAG, "getting Notifications for " + account.getFullName());
                        Response<List<Notification>> notifications =
                                mastodonApi.notificationsWithAuth(
                                        String.format("Bearer %s", account.getAccessToken()),
                                        account.getDomain()
                                        )
                                        .execute();
                        if (notifications.isSuccessful()) {
                            onNotificationsReceived(account, notifications.body());
                        } else {
                            Log.w(TAG, "error receiving notifications");
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "error receiving notifications", e);
                    }
                }

            }

            return Result.SUCCESS;
        }

        private void onNotificationsReceived(AccountEntity account, List<Notification> notificationList) {
            Collections.reverse(notificationList);
            BigInteger newId = new BigInteger(account.getLastNotificationId());
            BigInteger newestId = BigInteger.ZERO;
            boolean isFirstOfBatch = true;

            for (Notification notification : notificationList) {
                BigInteger currentId = new BigInteger(notification.getId());
                if (isBiggerThan(currentId, newestId)) {
                    newestId = currentId;
                }

                if (isBiggerThan(currentId, newId)) {
                    NotificationHelper.make(context, notification, account, isFirstOfBatch);
                    isFirstOfBatch = false;
                }
            }

            account.setLastNotificationId(newestId.toString());
            accountManager.saveAccount(account);
        }

        private boolean isBiggerThan(BigInteger newId, BigInteger lastShownNotificationId) {
            return lastShownNotificationId.compareTo(newId) < 0;
        }
    }
}
