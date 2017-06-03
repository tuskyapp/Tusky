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

package com.keylesspalace.tusky.util;

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
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.keylesspalace.tusky.MainActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.receiver.NotificationClearBroadcastReceiver;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;

public class NotificationMaker {

    public static final String TAG = "NotificationMaker";

    public static void make(final Context context, final int notifyId, Notification body) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences notificationPreferences = context.getSharedPreferences(
                "Notifications", Context.MODE_PRIVATE);

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
                if (currentNotifications.getString(i).equals(body.account.getDisplayName())) {
                    alreadyContains = true;
                }
            } catch (JSONException e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }

        if (!alreadyContains) {
            currentNotifications.put(body.account.getDisplayName());
        }

        notificationPreferences.edit()
                .putString("current", currentNotifications.toString())
                .commit();

        Intent resultIntent = new Intent(context, MainActivity.class);
        resultIntent.putExtra("tab_position", 1);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteIntent = new Intent(context, NotificationClearBroadcastReceiver.class);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(resultPendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setDefaults(0); // So it doesn't ring twice, notify only in Target callback

        if (currentNotifications.length() == 1) {
            builder.setContentTitle(titleForType(context, body))
                    .setContentText(truncateWithEllipses(bodyForType(body), 40));

            Target mTarget = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    builder.setLargeIcon(bitmap);

                    setupPreferences(preferences, builder);

                    ((NotificationManager) (context.getSystemService(Context.NOTIFICATION_SERVICE)))
                            .notify(notifyId, builder.build());
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {}

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {}
            };

            Picasso.with(context)
                    .load(body.account.avatar)
                    .placeholder(R.drawable.avatar_default)
                    .transform(new RoundedTransformation(7, 0))
                    .into(mTarget);
        } else {
            setupPreferences(preferences, builder);
            try {
                builder.setContentTitle(String.format(context.getString(R.string.notification_title_summary), currentNotifications.length()))
                        .setContentText(truncateWithEllipses(joinNames(context, currentNotifications), 40));
            } catch (JSONException e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            builder.setCategory(android.app.Notification.CATEGORY_SOCIAL);
        }

        ((NotificationManager) (context.getSystemService(Context.NOTIFICATION_SERVICE)))
                .notify(notifyId, builder.build());
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

    private static String truncateWithEllipses(String string, int limit) {
        if (string.length() < limit) {
            return string;
        } else {
            return string.substring(0, limit - 3) + "...";
        }
    }

    private static void setupPreferences(SharedPreferences preferences,
                                         NotificationCompat.Builder builder) {
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

    @Nullable
    private static String joinNames(Context context, JSONArray array) throws JSONException {
        if (array.length() > 3) {
            return String.format(context.getString(R.string.notification_summary_large), array.get(0), array.get(1), array.get(2), array.length() - 3);
        } else if (array.length() == 3) {
            return String.format(context.getString(R.string.notification_summary_medium), array.get(0), array.get(1), array.get(2));
        } else if (array.length() == 2) {
            return String.format(context.getString(R.string.notification_summary_small), array.get(0), array.get(1));
        }

        return null;
    }

    @Nullable
    private static String titleForType(Context context, Notification notification) {
        switch (notification.type) {
            case MENTION:
                return String.format(context.getString(R.string.notification_mention_format), notification.account.getDisplayName());
            case FOLLOW:
                return String.format(context.getString(R.string.notification_follow_format), notification.account.getDisplayName());
            case FAVOURITE:
                return String.format(context.getString(R.string.notification_favourite_format), notification.account.getDisplayName());
            case REBLOG:
                return String.format(context.getString(R.string.notification_reblog_format), notification.account.getDisplayName());
        }
        return null;
    }

    @Nullable
    private static String bodyForType(Notification notification) {
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
