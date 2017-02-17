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
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PullNotificationService extends IntentService {
    private static final int NOTIFY_ID = 6; // This is an arbitrary number.
    private static final String TAG = "PullNotifications";

    public PullNotificationService() {
        super("Tusky Pull Notification Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String domain = preferences.getString("domain", null);
        String accessToken = preferences.getString("accessToken", null);
        long date = preferences.getLong("lastUpdate", 0);
        Date lastUpdate = null;
        if (date != 0) {
            lastUpdate = new Date(date);
        }
        checkNotifications(domain, accessToken, lastUpdate);
    }

    private void checkNotifications(final String domain, final String accessToken,
            final Date lastUpdate) {
        String endpoint = getString(R.string.endpoint_notifications);
        String url = "https://" + domain + endpoint;
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        List<Notification> notifications;
                        try {
                            notifications = Notification.parse(response);
                        } catch (JSONException e) {
                            onCheckNotificationsFailure(e);
                            return;
                        }
                        onCheckNotificationsSuccess(notifications, lastUpdate);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onCheckNotificationsFailure(error);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onCheckNotificationsSuccess(List<Notification> notifications, Date lastUpdate) {
        Date newest = null;
        List<MentionResult> mentions = new ArrayList<>();
        for (Notification notification : notifications) {
            if (notification.getType() == Notification.Type.MENTION) {
                Status status = notification.getStatus();
                if (status != null) {
                    Date createdAt = status.getCreatedAt();
                    if (lastUpdate == null || createdAt.after(lastUpdate)) {
                        MentionResult mention = new MentionResult();
                        mention.content = status.getContent().toString();
                        mention.displayName = notification.getDisplayName();
                        mention.avatarUrl = status.getAvatar();
                        mentions.add(mention);
                    }
                    if (newest == null || createdAt.after(newest)) {
                        newest = createdAt;
                    }
                }
            }
        }
        long now = new Date().getTime();
        if (mentions.size() > 0) {
            SharedPreferences preferences = getSharedPreferences(
                    getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putLong("lastUpdate", now);
            editor.apply();
            loadAvatar(mentions, mentions.get(0).avatarUrl);
        } else if (newest != null) {
            long hoursAgo = (now - newest.getTime()) / (60 * 60 * 1000);
            if (hoursAgo >= 1) {
                dismissStaleNotifications();
            }
        }
    }

    private void onCheckNotificationsFailure(Exception exception) {
        //TODO: not sure if just logging here is enough?
        Log.e(TAG, "Failed to check notifications. " + exception.getMessage());
    }

    private static class MentionResult {
        public String displayName;
        public String content;
        public String avatarUrl;
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
            ImageRequest request = new ImageRequest(url, new Response.Listener<Bitmap>() {
                @Override
                public void onResponse(Bitmap response) {
                    updateNotification(mentions, response);
                }
            }, 0, 0, null, null, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    updateNotification(mentions, null);
                }
            });
            VolleySingleton.getInstance(this).addToRequestQueue(request);
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
            .setSmallIcon(R.mipmap.ic_notify)
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

    private void dismissStaleNotifications() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFY_ID);
    }
}
