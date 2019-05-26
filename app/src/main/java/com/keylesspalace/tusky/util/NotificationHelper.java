/* Copyright 2018 Jeremiasz Nelz <remi6397(a)gmail.com>
 * Copyright 2017 Andrew Dawson
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
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.text.BidiFormatter;

import android.text.TextUtils;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.FutureTarget;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.MainActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Poll;
import com.keylesspalace.tusky.entity.PollOption;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.receiver.NotificationClearBroadcastReceiver;
import com.keylesspalace.tusky.receiver.SendStatusBroadcastReceiver;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class NotificationHelper {

    private static int notificationId = 0;

    /**
     * constants used in Intents
     */
    public static final String ACCOUNT_ID = "account_id";

    private static final String TAG = "NotificationHelper";

    public static final String REPLY_ACTION = "REPLY_ACTION";

    public static final String COMPOSE_ACTION = "COMPOSE_ACTION";

    public static final String KEY_REPLY = "KEY_REPLY";

    public static final String KEY_SENDER_ACCOUNT_ID = "KEY_SENDER_ACCOUNT_ID";

    public static final String KEY_SENDER_ACCOUNT_IDENTIFIER = "KEY_SENDER_ACCOUNT_IDENTIFIER";

    public static final String KEY_SENDER_ACCOUNT_FULL_NAME = "KEY_SENDER_ACCOUNT_FULL_NAME";

    public static final String KEY_NOTIFICATION_ID = "KEY_NOTIFICATION_ID";

    public static final String KEY_CITED_STATUS_ID = "KEY_CITED_STATUS_ID";

    public static final String KEY_VISIBILITY = "KEY_VISIBILITY";

    public static final String KEY_SPOILER = "KEY_SPOILER";

    public static final String KEY_MENTIONS = "KEY_MENTIONS";

    public static final String KEY_CITED_TEXT = "KEY_CITED_TEXT";

    public static final String KEY_CITED_AUTHOR_LOCAL = "KEY_CITED_AUTHOR_LOCAL";

    /**
     * notification channels used on Android O+
     **/
    public static final String CHANNEL_MENTION = "CHANNEL_MENTION";
    public static final String CHANNEL_FOLLOW = "CHANNEL_FOLLOW";
    public static final String CHANNEL_BOOST = "CHANNEL_BOOST";
    public static final String CHANNEL_FAVOURITE = "CHANNEL_FAVOURITE";
    public static final String CHANNEL_POLL = "CHANNEL_POLL";

    /**
     * time in minutes between notification checks
     * note that this cannot be less than 15 minutes due to Android battery saving constraints
     */
    private static final int NOTIFICATION_CHECK_INTERVAL_MINUTES = 15;


    /**
     * Takes a given Mastodon notification and either creates a new Android notification or updates
     * the state of the existing notification to reflect the new interaction.
     *
     * @param context to access application preferences and services
     * @param body    a new Mastodon notification
     * @param account the account for which the notification should be shown
     */

    public static void make(final Context context, Notification body, AccountEntity account, boolean isFirstOfBatch) {

        if (!filterNotification(account, body, context)) {
            return;
        }

        String rawCurrentNotifications = account.getActiveNotifications();
        JSONArray currentNotifications;
        BidiFormatter bidiFormatter = BidiFormatter.getInstance();

        try {
            currentNotifications = new JSONArray(rawCurrentNotifications);
        } catch (JSONException e) {
            currentNotifications = new JSONArray();
        }

        for (int i = 0; i < currentNotifications.length(); i++) {
            try {
                if (currentNotifications.getString(i).equals(body.getAccount().getName())) {
                    currentNotifications.remove(i);
                    break;
                }
            } catch (JSONException e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }

        currentNotifications.put(body.getAccount().getName());

        account.setActiveNotifications(currentNotifications.toString());

        // Notification group member
        // =========================
        final NotificationCompat.Builder builder = newNotification(context, body, account, false);

        notificationId++;

        builder.setContentTitle(titleForType(context, body, bidiFormatter, account))
                .setContentText(bodyForType(body, context));

        if (body.getType() == Notification.Type.MENTION || body.getType() == Notification.Type.POLL) {
            builder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bodyForType(body, context)));
        }

        //load the avatar synchronously
        Bitmap accountAvatar;
        try {
            FutureTarget<Bitmap> target = Glide.with(context)
                    .asBitmap()
                    .load(body.getAccount().getAvatar())
                    .transform(new RoundedCorners(20))
                    .submit();

            accountAvatar = target.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.d(TAG, "error loading account avatar", e);
            accountAvatar = BitmapFactory.decodeResource(context.getResources(), R.drawable.avatar_default);
        }

        builder.setLargeIcon(accountAvatar);

        // Reply to mention action; RemoteInput is available from KitKat Watch, but buttons are available from Nougat
        if (body.getType() == Notification.Type.MENTION
                && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            RemoteInput replyRemoteInput = new RemoteInput.Builder(KEY_REPLY)
                    .setLabel(context.getString(R.string.label_quick_reply))
                    .build();

            PendingIntent quickReplyPendingIntent = getStatusReplyIntent(REPLY_ACTION, context, body, account);

            NotificationCompat.Action quickReplyAction =
                    new NotificationCompat.Action.Builder(R.drawable.ic_reply_24dp,
                            context.getString(R.string.action_quick_reply), quickReplyPendingIntent)
                            .addRemoteInput(replyRemoteInput)
                            .build();

            builder.addAction(quickReplyAction);

            PendingIntent composePendingIntent = getStatusReplyIntent(COMPOSE_ACTION, context, body, account);

            NotificationCompat.Action composeAction =
                    new NotificationCompat.Action.Builder(R.drawable.ic_reply_24dp,
                            context.getString(R.string.action_compose_shortcut), composePendingIntent)
                            .build();

            builder.addAction(composeAction);
        }

        builder.setSubText(account.getFullName());
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        builder.setCategory(NotificationCompat.CATEGORY_SOCIAL);
        builder.setOnlyAlertOnce(true);

        // only alert for the first notification of a batch to avoid multiple alerts at once
        if(!isFirstOfBatch) {
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
        }

        // Summary
        // =======
        final NotificationCompat.Builder summaryBuilder = newNotification(context, body, account, true);

        if (currentNotifications.length() != 1) {
            try {
                String title = context.getString(R.string.notification_title_summary, currentNotifications.length());
                String text = joinNames(context, currentNotifications, bidiFormatter);
                summaryBuilder.setContentTitle(title)
                        .setContentText(text);
            } catch (JSONException e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }

        summaryBuilder.setSubText(account.getFullName());
        summaryBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        summaryBuilder.setCategory(NotificationCompat.CATEGORY_SOCIAL);
        summaryBuilder.setOnlyAlertOnce(true);
        summaryBuilder.setGroupSummary(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        notificationManager.notify(notificationId, builder.build());
        if (currentNotifications.length() == 1) {
            notificationManager.notify((int) account.getId(), builder.setGroupSummary(true).build());
        } else {
            notificationManager.notify((int) account.getId(), summaryBuilder.build());
        }
    }

    private static NotificationCompat.Builder newNotification(Context context, Notification body, AccountEntity account, boolean summary) {
        Intent summaryResultIntent = new Intent(context, MainActivity.class);
        summaryResultIntent.putExtra(ACCOUNT_ID, account.getId());
        TaskStackBuilder summaryStackBuilder = TaskStackBuilder.create(context);
        summaryStackBuilder.addParentStack(MainActivity.class);
        summaryStackBuilder.addNextIntent(summaryResultIntent);

        PendingIntent summaryResultPendingIntent = summaryStackBuilder.getPendingIntent(notificationId,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // we have to switch account here
        Intent eventResultIntent = new Intent(context, MainActivity.class);
        eventResultIntent.putExtra(ACCOUNT_ID, account.getId());
        TaskStackBuilder eventStackBuilder = TaskStackBuilder.create(context);
        eventStackBuilder.addParentStack(MainActivity.class);
        eventStackBuilder.addNextIntent(eventResultIntent);

        PendingIntent eventResultPendingIntent = eventStackBuilder.getPendingIntent((int) account.getId(),
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteIntent = new Intent(context, NotificationClearBroadcastReceiver.class);
        deleteIntent.putExtra(ACCOUNT_ID, account.getId());
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(context, summary ? (int) account.getId() : notificationId, deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getChannelId(account, body))
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(summary ? summaryResultPendingIntent : eventResultPendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setColor(BuildConfig.DEBUG ? Color.parseColor("#19A341") : ContextCompat.getColor(context, R.color.tusky_blue))
                .setGroup(account.getAccountId())
                .setAutoCancel(true)
                .setDefaults(0); // So it doesn't ring twice, notify only in Target callback

        setupPreferences(account, builder);

        return builder;
    }

    private static PendingIntent getStatusReplyIntent(String action, Context context, Notification body, AccountEntity account) {
        Status status = body.getStatus();

        String citedLocalAuthor = status.getAccount().getLocalUsername();
        String citedText = status.getContent().toString();
        String inReplyToId = status.getId();
        Status actionableStatus = status.getActionableStatus();
        Status.Visibility replyVisibility = actionableStatus.getVisibility();
        String contentWarning = actionableStatus.getSpoilerText();
        Status.Mention[] mentions = actionableStatus.getMentions();
        List<String> mentionedUsernames = new ArrayList<>();
        mentionedUsernames.add(actionableStatus.getAccount().getUsername());
        for (Status.Mention mention : mentions) {
            mentionedUsernames.add(mention.getUsername());
        }
        mentionedUsernames.removeAll(Collections.singleton(account.getUsername()));
        mentionedUsernames = new ArrayList<>(new LinkedHashSet<>(mentionedUsernames));

        Intent replyIntent = new Intent(context, SendStatusBroadcastReceiver.class)
                .setAction(action)
                .putExtra(KEY_CITED_AUTHOR_LOCAL, citedLocalAuthor)
                .putExtra(KEY_CITED_TEXT, citedText)
                .putExtra(KEY_SENDER_ACCOUNT_ID, account.getId())
                .putExtra(KEY_SENDER_ACCOUNT_IDENTIFIER, account.getIdentifier())
                .putExtra(KEY_SENDER_ACCOUNT_FULL_NAME, account.getFullName())
                .putExtra(KEY_NOTIFICATION_ID, notificationId)
                .putExtra(KEY_CITED_STATUS_ID, inReplyToId)
                .putExtra(KEY_VISIBILITY, replyVisibility)
                .putExtra(KEY_SPOILER, contentWarning)
                .putExtra(KEY_MENTIONS, mentionedUsernames.toArray(new String[0]));

        return PendingIntent.getBroadcast(context.getApplicationContext(),
                notificationId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void createNotificationChannelsForAccount(@NonNull AccountEntity account, @NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            String[] channelIds = new String[]{
                    CHANNEL_MENTION + account.getIdentifier(),
                    CHANNEL_FOLLOW + account.getIdentifier(),
                    CHANNEL_BOOST + account.getIdentifier(),
                    CHANNEL_FAVOURITE + account.getIdentifier(),
                    CHANNEL_POLL + account.getIdentifier(),
            };
            int[] channelNames = {
                    R.string.notification_mention_name,
                    R.string.notification_follow_name,
                    R.string.notification_boost_name,
                    R.string.notification_favourite_name,
                    R.string.notification_poll_name
            };
            int[] channelDescriptions = {
                    R.string.notification_mention_descriptions,
                    R.string.notification_follow_description,
                    R.string.notification_boost_description,
                    R.string.notification_favourite_description,
                    R.string.notification_poll_description
            };

            List<NotificationChannel> channels = new ArrayList<>(5);

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

            notificationManager.createNotificationChannels(channels);

        }
    }

    public static void deleteNotificationChannelsForAccount(@NonNull AccountEntity account, @NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            notificationManager.deleteNotificationChannelGroup(account.getIdentifier());

        }
    }

    public static void deleteLegacyNotificationChannels(@NonNull Context context, @NonNull AccountManager accountManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // used until Tusky 1.4
            //noinspection ConstantConditions
            notificationManager.deleteNotificationChannel(CHANNEL_MENTION);
            notificationManager.deleteNotificationChannel(CHANNEL_FAVOURITE);
            notificationManager.deleteNotificationChannel(CHANNEL_BOOST);
            notificationManager.deleteNotificationChannel(CHANNEL_FOLLOW);

            // used until Tusky 1.7
            for(AccountEntity account: accountManager.getAllAccountsOrderedByActive()) {
                notificationManager.deleteNotificationChannel(CHANNEL_FAVOURITE+" "+account.getIdentifier());
            }
        }
    }

    public static boolean areNotificationsEnabled(@NonNull Context context, @NonNull AccountManager accountManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // on Android >= O, notifications are enabled, if at least one channel is enabled
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            if (notificationManager.areNotificationsEnabled()) {
                for (NotificationChannel channel : notificationManager.getNotificationChannels()) {
                    if (channel.getImportance() > NotificationManager.IMPORTANCE_NONE) {
                        Log.d(TAG, "NotificationsEnabled");
                        return true;
                    }
                }
            }
            Log.d(TAG, "NotificationsDisabled");

            return false;

        } else {
            // on Android < O, notifications are enabled, if at least one account has notification enabled
            return accountManager.areNotificationsEnabled();
        }

    }

    public static void enablePullNotifications() {
        long checkInterval = 1000 * 60 * NOTIFICATION_CHECK_INTERVAL_MINUTES;

        new JobRequest.Builder(NotificationPullJobCreator.NOTIFICATIONS_JOB_TAG)
                .setPeriodic(checkInterval)
                .setUpdateCurrent(true)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .build()
                .scheduleAsync();

        Log.d(TAG, "enabled notification checks with "+ NOTIFICATION_CHECK_INTERVAL_MINUTES + "min interval");
    }

    public static void disablePullNotifications() {
        JobManager.instance().cancelAllForTag(NotificationPullJobCreator.NOTIFICATIONS_JOB_TAG);
        Log.d(TAG, "disabled notification checks");

    }

    public static void clearNotificationsForActiveAccount(@NonNull Context context, @NonNull AccountManager accountManager) {
        AccountEntity account = accountManager.getActiveAccount();
        if (account != null && !account.getActiveNotifications().equals("[]")) {
            Single.fromCallable(() -> {
                account.setActiveNotifications("[]");
                accountManager.saveAccount(account);

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                //noinspection ConstantConditions
                notificationManager.cancel((int) account.getId());
                return true;
            })
            .subscribeOn(Schedulers.io())
            .subscribe();
        }
    }

    private static boolean filterNotification(AccountEntity account, Notification notification,
                                              Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = getChannelId(account, notification);
            if(channelId == null) {
                // unknown notificationtype
                return false;
            }
            //noinspection ConstantConditions
            NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
            return channel.getImportance() > NotificationManager.IMPORTANCE_NONE;
        }

        switch (notification.getType()) {
            case MENTION:
                return account.getNotificationsMentioned();
            case FOLLOW:
                return account.getNotificationsFollowed();
            case REBLOG:
                return account.getNotificationsReblogged();
            case FAVOURITE:
                return account.getNotificationsFavorited();
            case POLL:
                return account.getNotificationsPolls();
            default:
                return false;
        }
    }

    private static @Nullable String getChannelId(AccountEntity account, Notification notification) {
        switch (notification.getType()) {
            case MENTION:
                return CHANNEL_MENTION + account.getIdentifier();
            case FOLLOW:
                return CHANNEL_FOLLOW + account.getIdentifier();
            case REBLOG:
                return CHANNEL_BOOST + account.getIdentifier();
            case FAVOURITE:
                return CHANNEL_FAVOURITE + account.getIdentifier();
            case POLL:
                return CHANNEL_POLL + account.getIdentifier();
            default:
                return null;
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

    private static String wrapItemAt(JSONArray array, int index, BidiFormatter bidiFormatter) throws JSONException {
        return bidiFormatter.unicodeWrap(array.get(index).toString());
    }

    @Nullable
    private static String joinNames(Context context, JSONArray array, BidiFormatter bidiFormatter) throws JSONException {
        if (array.length() > 3) {
            int length = array.length();
            return String.format(context.getString(R.string.notification_summary_large),
                    wrapItemAt(array, length - 1, bidiFormatter),
                    wrapItemAt(array, length - 2, bidiFormatter),
                    wrapItemAt(array, length - 3, bidiFormatter),
                    length - 3);
        } else if (array.length() == 3) {
            return String.format(context.getString(R.string.notification_summary_medium),
                    wrapItemAt(array, 2, bidiFormatter),
                    wrapItemAt(array, 1, bidiFormatter),
                    wrapItemAt(array, 0, bidiFormatter));
        } else if (array.length() == 2) {
            return String.format(context.getString(R.string.notification_summary_small),
                    wrapItemAt(array, 1, bidiFormatter),
                    wrapItemAt(array, 0, bidiFormatter));
        }

        return null;
    }

    @Nullable
    private static String titleForType(Context context, Notification notification, BidiFormatter bidiFormatter, AccountEntity account) {
        String accountName = bidiFormatter.unicodeWrap(notification.getAccount().getName());
        switch (notification.getType()) {
            case MENTION:
                return String.format(context.getString(R.string.notification_mention_format),
                        accountName);
            case FOLLOW:
                return String.format(context.getString(R.string.notification_follow_format),
                        accountName);
            case FAVOURITE:
                return String.format(context.getString(R.string.notification_favourite_format),
                        accountName);
            case REBLOG:
                return String.format(context.getString(R.string.notification_reblog_format),
                        accountName);
            case POLL:
                if(notification.getStatus().getAccount().getId().equals(account.getAccountId())) {
                    return context.getString(R.string.poll_ended_created);
                } else {
                    return context.getString(R.string.poll_ended_voted);
                }
        }
        return null;
    }

    private static String bodyForType(Notification notification, Context context) {
        switch (notification.getType()) {
            case FOLLOW:
                return "@" + notification.getAccount().getUsername();
            case MENTION:
            case FAVOURITE:
            case REBLOG:
                if (!TextUtils.isEmpty(notification.getStatus().getSpoilerText())) {
                    return notification.getStatus().getSpoilerText();
                } else {
                    return notification.getStatus().getContent().toString();
                }
            case POLL:
                if (!TextUtils.isEmpty(notification.getStatus().getSpoilerText())) {
                    return notification.getStatus().getSpoilerText();
                } else {
                    StringBuilder builder = new StringBuilder(notification.getStatus().getContent());
                    builder.append('\n');
                    Poll poll = notification.getStatus().getPoll();
                    for(PollOption option: poll.getOptions()) {
                        int percent = option.getPercent(poll.getVotesCount());
                        CharSequence optionText = HtmlUtils.fromHtml(context.getString(R.string.poll_option_format, percent, option.getTitle()));
                        builder.append(optionText);
                        builder.append('\n');
                    }
                    return builder.toString();
                }
        }
        return null;
    }

}
