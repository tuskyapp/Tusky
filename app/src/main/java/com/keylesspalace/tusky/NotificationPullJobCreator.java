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

package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spanned;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.network.AuthInterceptor;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.util.NotificationManager;
import com.keylesspalace.tusky.util.OkHttpUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by charlag on 31/10/17.
 */

public final class NotificationPullJobCreator implements JobCreator {

    public static final int NOTIFY_ID = 6; // chosen by fair dice roll, guaranteed to be random
    static final String NOTIFICATIONS_JOB_TAG = "notifications_job_tag";

    private Context context;

    NotificationPullJobCreator(Context context) {
        this.context = context;
    }

    @Nullable
    @Override
    public Job create(@NonNull String tag) {
        if (tag.equals(NOTIFICATIONS_JOB_TAG)) {
            final AccountEntity account = TuskyApplication.getAccountManager().getActiveAccount();

            if(account != null) {
                return new NotificationPullJob(account.getDomain(), context);
            }
        }
        return null;
    }

    private static MastodonApi createMastodonApi(String domain) {

        OkHttpClient okHttpClient = OkHttpUtils.getCompatibleClientBuilder()
                .addInterceptor(new AuthInterceptor())
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return retrofit.create(MastodonApi.class);
    }

    private final static class NotificationPullJob extends Job {

        @NonNull private MastodonApi mastodonApi;
        private Context context;

        NotificationPullJob(String domain, Context context) {
            this.mastodonApi = createMastodonApi(domain);
            this.context = context;
        }

        @NonNull
        @Override
        protected Result onRunJob(Params params) {
            try {
                Response<List<Notification>> notifications =
                        mastodonApi.notifications(null, null, null).execute();
                if (notifications.isSuccessful()) {
                    onNotificationsReceived(notifications.body());
                } else {
                    return Result.FAILURE;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return Result.FAILURE;
            }
            return Result.SUCCESS;
        }

        private void onNotificationsReceived(List<Notification> notificationList) {
            SharedPreferences notificationsPreferences = context.getSharedPreferences(
                    "Notifications", Context.MODE_PRIVATE);
            //make a copy of the string set, the returned instance should not be modified
            Set<String> currentIds = new HashSet<>(notificationsPreferences.getStringSet(
                    "current_ids", Collections.emptySet()));
            for (Notification notification : notificationList) {
                String id = notification.id;
                if (!currentIds.contains(id)) {
                    currentIds.add(id);
                    NotificationManager.make(context, NOTIFY_ID, notification);
                }
            }
            notificationsPreferences.edit()
                    .putStringSet("current_ids", currentIds)
                    .apply();
        }
    }
}
