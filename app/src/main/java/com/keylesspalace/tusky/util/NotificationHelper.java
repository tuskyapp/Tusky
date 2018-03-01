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

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.keylesspalace.tusky.MainActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.TuskyApplication;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.receiver.NotificationClearBroadcastReceiver;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NotificationHelper {

    /** constants used in Intents */
    public static final String ACCOUNT_ID = "account_id";

    private static final String TAG = "NotificationHelper";

    /** notification channels used on Android O+ **/
    private static final String CHANNEL_MENTION = "CHANNEL_MENTION";
    private static final String CHANNEL_FOLLOW = "CHANNEL_FOLLOW";
    private static final String CHANNEL_BOOST = "CHANNEL_BOOST";
    private static final String CHANNEL_FAVOURITE = " CHANNEL_FAVOURITE";

    /**
     * Takes a given Mastodon notification and either creates a new Android notification or updates
     * the state of the existing notification to reflect the new interaction.
     *
     * @param context  to access application preferences and services
     * @param body     a new Mastodon notification
     * @param account  the account for which the notification should be shown
     */

    public static void make(final Context context, Notification body, AccountEntity account) {

        if (!filterNotification(account, body, context)) {
            return;
        }

        String rawCurrentNotifications = account.getActiveNotifications();
        JSONArray currentNotifications;

        try {
            currentNotifications = new JSONArray(rawCurrentNotifications);
        } catch (JSONException e) {
            currentNotifications = new JSONArray();
        }

        boolean alreadyContains = false;

        for (int i = 0; i < currentNotifications.length(); i++) {
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

        account.setActiveNotifications(currentNotifications.toString());

        //no need to save account, this will be done in the calling function

        Intent resultIntent = new Intent(context, MainActivity.class);
        resultIntent.putExtra(ACCOUNT_ID, account.getId());
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent((int)account.getId(),
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteIntent = new Intent(context, NotificationClearBroadcastReceiver.class);
        deleteIntent.putExtra(ACCOUNT_ID, account.getId());
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(context, (int)account.getId(), deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getChannelId(account, body))
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(resultPendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setColor(ContextCompat.getColor(context, (R.color.primary)))
                .setDefaults(0); // So it doesn't ring twice, notify only in Target callback

        setupPreferences(account, builder);

        if (currentNotifications.length() == 1) {
            builder.setContentTitle(titleForType(context, body))
                    .setContentText(bodyForType(body));

            if(body.type == Notification.Type.MENTION) {
                builder.setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(bodyForType(body)));
            }

            //load the avatar synchronously
            Bitmap accountAvatar;
            try {
                accountAvatar = Picasso.with(context)
                        .load(body.account.avatar)
                        .transform(new RoundedTransformation(7, 0))
                        .get();
            } catch (IOException e) {
                Log.d(TAG, "error loading account avatar", e);
                accountAvatar = BitmapFactory.decodeResource(context.getResources(), R.drawable.avatar_default);
            }

            builder.setLargeIcon(accountAvatar);

        } else {
            try {
                String title = context.getString(R.string.notification_title_summary, currentNotifications.length());
                String text = joinNames(context, currentNotifications);
                builder.setContentTitle(title)
                        .setContentText(text);
            } catch (JSONException e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }

        builder.setSubText(account.getFullName());

        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        builder.setCategory(NotificationCompat.CATEGORY_SOCIAL);

        builder.setOnlyAlertOnce(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        //noinspection ConstantConditions
        notificationManager.notify((int)account.getId(), builder.build());
    }

    public static void createNotificationChannelsForAccount(AccountEntity account, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            String[] channelIds = new String[]{
                    CHANNEL_MENTION+account.getIdentifier(),
                    CHANNEL_FOLLOW+account.getIdentifier(),
                    CHANNEL_BOOST+account.getIdentifier(),
                    CHANNEL_FAVOURITE+account.getIdentifier()};
            int[] channelNames = {
                    R.string.notification_channel_mention_name,
                    R.string.notification_channel_follow_name,
                    R.string.notification_channel_boost_name,
                    R.string.notification_channel_favourite_name
            };
            int[] channelDescriptions = {
                    R.string.notification_channel_mention_descriptions,
                    R.string.notification_channel_follow_description,
                    R.string.notification_channel_boost_description,
                    R.string.notification_channel_favourite_description
            };

            List<NotificationChannel> channels = new ArrayList<>(4);

            NotificationChannelGroup channelGroup = new NotificationChannelGroup(account.getIdentifier(), account.getFullName());

            //noinspection ConstantConditions
            notificationManager.createNotificationChannelGroup(channelGroup);

            for (int i = 0; i < channelIds.length; i++) {
                String id = channelIds[i];
                String name = context.getString(channelNames[i]);
                String description = context.getString(channelDescriptions[i]);
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(id, name, importance);

                channel.setDescription(description);
                channel.enableLights(true);
                channel.setLightColor(0xFF2B90D9);
                channel.enableVibration(true);
                channel.setShowBadge(true);
                channel.setGroup(account.getIdentifier());
                channels.add(channel);
            }

            //noinspection ConstantConditions
            notificationManager.createNotificationChannels(channels);

        }
    }

    public static void deleteNotificationChannelsForAccount(AccountEntity account, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            notificationManager.deleteNotificationChannelGroup(account.getIdentifier());

        }
    }

    public static void deleteLegacyNotificationChannels(Context context) {
        // delete the notification channels that where used before the multi account mode was introduced to avoid confusion
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            notificationManager.deleteNotificationChannel(CHANNEL_MENTION);
            notificationManager.deleteNotificationChannel(CHANNEL_FAVOURITE);
            notificationManager.deleteNotificationChannel(CHANNEL_BOOST);
            notificationManager.deleteNotificationChannel(CHANNEL_FOLLOW);
        }
    }

    public static boolean areNotificationsEnabled(Context context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // on Android >= O, notifications are enabled, if at least one channel is enabled
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            if(notificationManager.areNotificationsEnabled()) {
                for(NotificationChannel channel: notificationManager.getNotificationChannels()) {
                    if(channel.getImportance() > NotificationManager.IMPORTANCE_NONE) {
                        Log.d(TAG, "NotificationsEnabled");
                        return true;
                    }
                }
            }
            Log.d(TAG, "NotificationsDisabled");

            return false;

        } else {
            // on Android < O, notifications are enabled, if at least one account has notification enabled
            return TuskyApplication.getAccountManager().areNotificationsEnabled();
        }

    }

    public static void clearNotificationsForActiveAccount(Context context) {
        AccountManager accountManager = TuskyApplication.getAccountManager();
        AccountEntity account = accountManager.getActiveAccount();
        if (account != null) {
            account.setActiveNotifications("[]");
            accountManager.saveAccount(account);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            //noinspection ConstantConditions
            notificationManager.cancel((int)account.getId());
        }
    }

    private static boolean filterNotification(AccountEntity account, Notification notification,
                                              Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            NotificationChannel channel = notificationManager.getNotificationChannel(getChannelId(account, notification));
            return channel.getImportance() > NotificationManager.IMPORTANCE_NONE;
        }

        switch (notification.type) {
            default:
            case MENTION:
                return account.getNotificationsMentioned();
            case FOLLOW:
                return account.getNotificationsFollowed();
            case REBLOG:
                return account.getNotificationsReblogged();
            case FAVOURITE:
                return account.getNotificationsFavorited();
        }
    }

    private static String getChannelId(AccountEntity account, Notification notification) {
        switch (notification.type) {
            default:
            case MENTION:
                return CHANNEL_MENTION+account.getIdentifier();
            case FOLLOW:
                return CHANNEL_FOLLOW+account.getIdentifier();
            case REBLOG:
                return CHANNEL_BOOST+account.getIdentifier();
            case FAVOURITE:
                return CHANNEL_FAVOURITE+account.getIdentifier();
        }

    }

    private static void setupPreferences(AccountEntity account,
                                         NotificationCompat.Builder builder) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return;  //do nothing on Android O or newer, the system uses the channel settings anyway
        }

        if (account.getNotificationSound()) {
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }

        if (account.getNotificationVibration()) {
            builder.setVibrate(new long[]{500, 500});
        }

        if (account.getNotificationLight()) {
            builder.setLights(0xFF2B90D9, 300, 1000);
        }
    }

    @Nullable
    private static String joinNames(Context context, JSONArray array) throws JSONException {
        if (array.length() > 3) {
            int length = array.length();
            return String.format(context.getString(R.string.notification_summary_large),
                    array.get(length-1), array.get(length-2), array.get(length-3), length - 3);
        } else if (array.length() == 3) {
            return String.format(context.getString(R.string.notification_summary_medium),
                    array.get(2), array.get(1), array.get(0));
        } else if (array.length() == 2) {
            return String.format(context.getString(R.string.notification_summary_small),
                    array.get(1), array.get(0));
        }

        return null;
    }

    @Nullable
    private static String titleForType(Context context, Notification notification) {
        switch (notification.type) {
            case MENTION:
                return String.format(context.getString(R.string.notification_mention_format),
                        notification.account.getDisplayName());
            case FOLLOW:
                return String.format(context.getString(R.string.notification_follow_format),
                        notification.account.getDisplayName());
            case FAVOURITE:
                return String.format(context.getString(R.string.notification_favourite_format),
                        notification.account.getDisplayName());
            case REBLOG:
                return String.format(context.getString(R.string.notification_reblog_format),
                        notification.account.getDisplayName());
        }
        return null;
    }

    @Nullable
    private static String bodyForType(Notification notification) {
        switch (notification.type) {
            case FOLLOW:
                return "@"+notification.account.username;
            case MENTION:
            case FAVOURITE:
            case REBLOG:
                return notification.status.content.toString();
        }
        return null;
    }

}
