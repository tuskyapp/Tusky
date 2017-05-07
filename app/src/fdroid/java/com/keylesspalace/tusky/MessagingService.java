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

package com.keylesspalace.tusky;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Spanned;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.json.StringWithEmoji;
import com.keylesspalace.tusky.json.StringWithEmojiTypeAdapter;
import com.keylesspalace.tusky.network.MastodonAPI;
import com.keylesspalace.tusky.util.NotificationMaker;
import com.keylesspalace.tusky.util.OkHttpUtils;

import java.util.HashSet;
import java.util.List;

import java.io.IOException;
import java.util.Set;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MessagingService extends IntentService {
    public static final int NOTIFY_ID = 6; // This is an arbitrary number.

    private MastodonAPI mastodonAPI;

    public MessagingService() {
        super("Tusky Pull Notification Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        boolean enabled = preferences.getBoolean("notificationsEnabled", true);
        if (!enabled) {
            return;
        }

        createMastodonApi();

        mastodonAPI.notifications(null, null, null).enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call,
                                   Response<List<Notification>> response) {
                if (response.isSuccessful()) {
                    onNotificationsReceived(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable t) {}
        });
    }

    private void createMastodonApi() {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        final String domain = preferences.getString("domain", null);
        final String accessToken = preferences.getString("accessToken", null);

        OkHttpClient okHttpClient = OkHttpUtils.getCompatibleClientBuilder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request originalRequest = chain.request();

                        Request.Builder builder = originalRequest.newBuilder()
                                .header("Authorization", String.format("Bearer %s", accessToken));

                        Request newRequest = builder.build();

                        return chain.proceed(newRequest);
                    }
                })
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .registerTypeAdapter(StringWithEmoji.class, new StringWithEmojiTypeAdapter())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        mastodonAPI = retrofit.create(MastodonAPI.class);
    }

    private void onNotificationsReceived(List<Notification> notificationList) {
        SharedPreferences notificationsPreferences = getSharedPreferences(
                "Notifications", Context.MODE_PRIVATE);
        Set<String> currentIds = notificationsPreferences.getStringSet(
                "current_ids", new HashSet<String>());
        for (Notification notification : notificationList) {
            String id = notification.id;
            if (!currentIds.contains(id)) {
                currentIds.add(id);
                NotificationMaker.make(this, NOTIFY_ID, notification);
            }
        }
        notificationsPreferences.edit()
                .putStringSet("current_ids", currentIds)
                .apply();
    }

    public static String getInstanceToken() {
        // This is only used for the "google" build flavor, so this version is just a stub method.
        return null;
    }
}
