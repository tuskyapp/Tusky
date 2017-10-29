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

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.service.PullNotificationService;
import com.keylesspalace.tusky.util.OkHttpUtils;

import java.io.IOException;

import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BaseActivity extends AppCompatActivity {
    protected static final int SERVICE_REQUEST_CODE = 8574603; // This number is arbitrary.

    public MastodonApi mastodonApi;
    protected Dispatcher mastodonApiDispatcher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        redirectIfNotLoggedIn();
        createMastodonApi();

        /* There isn't presently a way to globally change the theme of a whole application at
         * runtime, just individual activities. So, each activity has to set its theme before any
         * views are created. */
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("lightTheme", false)) {
            setTheme(R.style.AppTheme_Light);
        }
    }

    @Override
    protected void onDestroy() {
        if (mastodonApiDispatcher != null) {
            mastodonApiDispatcher.cancelAll();
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransitionExit();
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransitionEnter();
    }

    private void overridePendingTransitionEnter() {
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
    }

    private void overridePendingTransitionExit() {
        overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
    }

    protected SharedPreferences getPrivatePreferences() {
        return getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
    }

    protected String getAccessToken() {
        SharedPreferences preferences = getPrivatePreferences();
        return preferences.getString("accessToken", null);
    }

    protected boolean arePushNotificationsEnabled() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean("notificationsEnabled", true);
    }

    protected String getBaseUrl() {
        SharedPreferences preferences = getPrivatePreferences();
        return "https://" + preferences.getString("domain", null);
    }

    protected void createMastodonApi() {
        mastodonApiDispatcher = new Dispatcher();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .create();

        OkHttpClient.Builder okBuilder =
                OkHttpUtils.getCompatibleClientBuilder().addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request originalRequest = chain.request();

                        Request.Builder builder = originalRequest.newBuilder();
                        String accessToken = getAccessToken();
                        if (accessToken != null) {
                            builder.header("Authorization", String.format("Bearer %s", accessToken));
                        }
                        Request newRequest = builder.build();

                        return chain.proceed(newRequest);
                    }
                }).dispatcher(mastodonApiDispatcher);

        if (BuildConfig.DEBUG) {
            okBuilder.addInterceptor(
                    new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC));
        }

        Retrofit retrofit = new Retrofit.Builder().baseUrl(getBaseUrl())
                .client(okBuilder.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        mastodonApi = retrofit.create(MastodonApi.class);
    }

    protected void redirectIfNotLoggedIn() {
        SharedPreferences preferences = getPrivatePreferences();
        String domain = preferences.getString("domain", null);
        String accessToken = preferences.getString("accessToken", null);
        if (domain == null || accessToken == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        TypedValue value = new TypedValue();
        int color;
        if (getTheme().resolveAttribute(R.attr.toolbar_icon_tint, value, true)) {
            color = value.data;
        } else {
            color = Color.WHITE;
        }
        for (int i = 0; i < menu.size(); i++) {
            Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) {
                icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    protected void enablePushNotifications() {
        // Start up the PullNotificationService on a repeating interval.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String minutesString = preferences.getString("pullNotificationCheckInterval", "15");
        long minutes = Long.valueOf(minutesString);
        long checkInterval = 1000 * 60 * minutes;
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, PullNotificationService.class);
        PendingIntent serviceAlarmIntent = PendingIntent.getService(this, SERVICE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                checkInterval, serviceAlarmIntent);
    }

    protected void disablePushNotifications() {
        // Cancel the repeating call for "pull" notifications.
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, PullNotificationService.class);
        PendingIntent serviceAlarmIntent = PendingIntent.getService(this, SERVICE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(serviceAlarmIntent);
    }

    protected void clearNotifications() {
        SharedPreferences notificationPreferences =
                getApplicationContext().getSharedPreferences("Notifications", MODE_PRIVATE);
        notificationPreferences.edit().putString("current", "[]").apply();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(PullNotificationService.NOTIFY_ID);
    }

    protected void setPullNotificationCheckInterval(long minutes) {
        long checkInterval = 1000 * 60 * minutes;
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, PullNotificationService.class);
        PendingIntent serviceAlarmIntent = PendingIntent.getService(this, SERVICE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(serviceAlarmIntent);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                checkInterval, serviceAlarmIntent);
    }
}
