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
 * see <http://www.gnu.org/licenses>.
 *
 * If you modify this Program, or any covered work, by linking or combining it with Firebase Cloud
 * Messaging and Firebase Crash Reporting (or a modified version of those libraries), containing
 * parts covered by the Google APIs Terms of Service, the licensors of this Program grant you
 * additional permission to convey the resulting work. */

package com.keylesspalace.tusky;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Spanned;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.entity.Notification;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private MastodonAPI mastodonAPI;
    private static final String TAG = "MyFirebaseMessagingService";
    public static final int NOTIFY_ID = 666;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, remoteMessage.getFrom());
        Log.d(TAG, remoteMessage.toString());

        String notificationId = remoteMessage.getData().get("notification_id");

        if (notificationId == null) {
            Log.e(TAG, "No notification ID in payload!!");
            return;
        }

        Log.d(TAG, notificationId);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        boolean enabled = preferences.getBoolean("notificationsEnabled", true);
        if (!enabled) {
            return;
        }

        createMastodonAPI();

        mastodonAPI.notification(notificationId).enqueue(new Callback<Notification>() {
            @Override
            public void onResponse(Call<Notification> call, Response<Notification> response) {
                if (response.isSuccessful()) {
                    buildNotification(response.body());
                }
            }

            @Override
            public void onFailure(Call<Notification> call, Throwable t) {

            }
        });
    }

    private void createMastodonAPI() {
        SharedPreferences preferences = getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
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
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        mastodonAPI = retrofit.create(MastodonAPI.class);
    }

    private String truncateWithEllipses(String string, int limit) {
        if (string.length() < limit) {
            return string;
        } else {
            return string.substring(0, limit - 3) + "...";
        }
    }

    private static boolean filterNotification(SharedPreferences preferences,
            Notification notification) {
        switch (notification.type) {
            default:
            case MENTION: {
                return preferences.getBoolean("notificationFilterMentions", true);
            }
            case FOLLOW: {
                return preferences.getBoolean("notificationFilterFollows", true);
            }
            case REBLOG: {
                return preferences.getBoolean("notificationFilterReblogs", true);
            }
            case FAVOURITE: {
                return preferences.getBoolean("notificationFilterFavourites", true);
            }
        }
    }

    private void buildNotification(Notification body) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences notificationPreferences = getApplicationContext().getSharedPreferences("Notifications", MODE_PRIVATE);

        if (!filterNotification(preferences, body)) {
            return;
        }

        String rawCurrentNotifications = notificationPreferences.getString("current", "[]");
        JSONArray currentNotifications;

        try {
            currentNotifications = new JSONArray(rawCurrentNotifications);
        } catch (JSONException e) {
            currentNotifications = new JSONArray();
        }

        boolean alreadyContains = false;

        for(int i = 0; i < currentNotifications.length(); i++) {
            try {
                if (currentNotifications.getString(i).equals(body.account.displayName)) {
                    alreadyContains = true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (!alreadyContains) {
            currentNotifications.put(body.account.displayName);
        }

        SharedPreferences.Editor editor = notificationPreferences.edit();
        editor.putString("current", currentNotifications.toString());
        editor.commit();

        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.putExtra("tab_position", 1);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteIntent = new Intent(this, NotificationClearBroadcastReceiver.class);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(this, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(resultPendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setDefaults(0); // So it doesn't ring twice, notify only in Target callback

        if (currentNotifications.length() == 1) {
            builder.setContentTitle(titleForType(body))
                    .setContentText(truncateWithEllipses(bodyForType(body), 40));

            Target mTarget = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    builder.setLargeIcon(bitmap);

                    setupPreferences(preferences, builder);

                    ((NotificationManager) (getSystemService(NOTIFICATION_SERVICE))).notify(NOTIFY_ID, builder.build());
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {

                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            };

            Picasso.with(this)
                    .load(body.account.avatar)
                    .placeholder(R.drawable.avatar_default)
                    .transform(new RoundedTransformation(7, 0))
                    .into(mTarget);
        } else {
            setupPreferences(preferences, builder);

            try {
                builder.setContentTitle(String.format(getString(R.string.notification_title_summary), currentNotifications.length()))
                        .setContentText(truncateWithEllipses(joinNames(currentNotifications), 40));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            builder.setCategory(android.app.Notification.CATEGORY_SOCIAL);
        }

        ((NotificationManager) (getSystemService(NOTIFICATION_SERVICE))).notify(NOTIFY_ID, builder.build());
    }

    private void setupPreferences(SharedPreferences preferences, NotificationCompat.Builder builder) {
        if (preferences.getBoolean("notificationAlertSound", true)) {
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }

        if (preferences.getBoolean("notificationAlertVibrate", false)) {
            builder.setVibrate(new long[] { 500, 500 });
        }

        if (preferences.getBoolean("notificationAlertLight", false)) {
            builder.setLights(0xFF00FF8F, 300, 1000);
        }
    }

    private String joinNames(JSONArray array) throws JSONException {
        if (array.length() > 3) {
            return String.format(getString(R.string.notification_summary_large), array.get(0), array.get(1), array.get(2), array.length() - 3);
        } else if (array.length() == 3) {
            return String.format(getString(R.string.notification_summary_medium), array.get(0), array.get(1), array.get(2));
        } else if (array.length() == 2) {
            return String.format(getString(R.string.notification_summary_small), array.get(0), array.get(1));
        }

        return null;
    }

    private String titleForType(Notification notification) {
        switch (notification.type) {
            case MENTION:
                return String.format(getString(R.string.notification_mention_format), notification.account.getDisplayName());
            case FOLLOW:
                return String.format(getString(R.string.notification_follow_format), notification.account.getDisplayName());
            case FAVOURITE:
                return String.format(getString(R.string.notification_favourite_format), notification.account.getDisplayName());
            case REBLOG:
                return String.format(getString(R.string.notification_reblog_format), notification.account.getDisplayName());
        }

        return null;
    }

    private String bodyForType(Notification notification) {
        switch (notification.type) {
            case FOLLOW:
                return notification.account.username;
            case MENTION:
            case FAVOURITE:
            case REBLOG:
                return notification.status.content.toString();
        }

        return null;
    }
}
