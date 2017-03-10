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

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Spanned;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.entity.*;
import com.keylesspalace.tusky.entity.Notification;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PullNotificationService extends IntentService {
    static final int NOTIFY_ID = 6; // This is an arbitrary number.
    private static final String TAG = "PullNotifications"; // logging tag

    public PullNotificationService() {
        super("Tusky Pull Notification Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String domain = preferences.getString("domain", null);
        String accessToken = preferences.getString("accessToken", null);
        String lastUpdateId = preferences.getString("lastUpdateId", null);
        checkNotifications(domain, accessToken, lastUpdateId);
    }

    private void checkNotifications(final String domain, final String accessToken,
            final String lastUpdateId) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
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
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        MastodonAPI api = retrofit.create(MastodonAPI.class);

        api.notifications(null, lastUpdateId, null).enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call, retrofit2.Response<List<Notification>> response) {
                onCheckNotificationsSuccess(response.body(), lastUpdateId);
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable t) {
                onCheckNotificationsFailure((Exception) t);
            }
        });
    }

    private void onCheckNotificationsSuccess(List<com.keylesspalace.tusky.entity.Notification> notifications, String lastUpdateId) {
        List<MentionResult> mentions = new ArrayList<>();

        for (com.keylesspalace.tusky.entity.Notification notification : notifications) {
            if (notification.type == com.keylesspalace.tusky.entity.Notification.Type.MENTION) {
                Status status = notification.status;

                if (status != null) {
                    MentionResult mention = new MentionResult();
                    mention.content = status.content.toString();
                    mention.displayName = notification.account.displayName;
                    mention.avatarUrl = status.account.avatar;
                    mentions.add(mention);
                }
            }
        }

        if (notifications.size() > 0) {
            SharedPreferences preferences = getSharedPreferences(
                    getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("lastUpdateId", notifications.get(0).id);
            editor.apply();
        }

        if (mentions.size() > 0) {
            loadAvatar(mentions, mentions.get(0).avatarUrl);
        }
    }

    private void onCheckNotificationsFailure(Exception exception) {
        Log.e(TAG, "Failed to check notifications. " + exception.getMessage());
    }

    private static class MentionResult {
        String displayName;
        String content;
        String avatarUrl;
    }

    private String truncateWithEllipses(String string, int limit) {
        if (string.length() < limit) {
            return string;
        } else {
            return string.substring(0, limit - 3) + "...";
        }
    }

    private void loadAvatar(final List<MentionResult> mentions, String url) {
        if (url != null) {
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    updateNotification(mentions, bitmap);
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    updateNotification(mentions, null);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {}
            };
            Picasso.with(this)
                    .load(url)
                    .into(target);
        } else {
            updateNotification(mentions, null);
        }
    }

    private void updateNotification(List<MentionResult> mentions, @Nullable Bitmap icon) {
        final int NOTIFICATION_CONTENT_LIMIT = 40;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String title;
        if (mentions.size() > 1) {
            title = String.format(
                    getString(R.string.notification_service_several_mentions),
                    mentions.size());
        } else {
            title = String.format(
                    getString(R.string.notification_service_one_mention),
                    mentions.get(0).displayName);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title);
        if (icon != null) {
            builder.setLargeIcon(icon);
        }
        if (preferences.getBoolean("notificationAlertSound", true)) {
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }
        if (preferences.getBoolean("notificationStyleVibrate", false)) {
            builder.setVibrate(new long[] { 500, 500 });
        }
        if (preferences.getBoolean("notificationStyleLight", false)) {
            builder.setLights(0xFF00FF8F, 300, 1000);
        }
        for (int i = 0; i < mentions.size(); i++) {
            MentionResult mention = mentions.get(i);
            String text = truncateWithEllipses(mention.content, NOTIFICATION_CONTENT_LIMIT);
            builder.setContentText(text)
                    .setNumber(i);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            builder.setCategory(android.app.Notification.CATEGORY_SOCIAL);
        }
        Intent resultIntent = new Intent(this, SplashActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(SplashActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_ID, builder.build());
    }
}
