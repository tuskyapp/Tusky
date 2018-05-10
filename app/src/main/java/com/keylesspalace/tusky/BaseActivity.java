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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.Menu;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.util.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Call;

public abstract class BaseActivity extends AppCompatActivity implements Injectable {

    protected List<Call> callList;

    @Inject
    public AccountManager accountManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        /* There isn't presently a way to globally change the theme of a whole application at
         * runtime, just individual activities. So, each activity has to set its theme before any
         * views are created. */
        String theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT);
        ThemeUtils.setAppNightMode(theme, this);

        long accountId = getIntent().getLongExtra("account", -1);
        if (accountId != -1) {
            accountManager.setActiveAccount(accountId);
        }

        int style;
        switch (preferences.getString("statusTextSize", "medium")) {
            case "large":
                style = R.style.TextSizeLarge;
                break;
            case "small":
                style = R.style.TextSizeSmall;
                break;
            case "medium":
            default:
                style = R.style.TextSizeMedium;
                break;

        }
        getTheme().applyStyle(style, false);

        redirectIfNotLoggedIn();

        callList = new ArrayList<>();

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

    protected void redirectIfNotLoggedIn() {
        AccountEntity account = accountManager.getActiveAccount();
        if (account == null) {
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
        // schedule job to pull notifications
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String minutesString = preferences.getString("pullNotificationCheckInterval", "15");
        long minutes = Long.valueOf(minutesString);
        if (minutes < 15) {
            preferences.edit().putString("pullNotificationCheckInterval", "15").apply();
            minutes = 15;
        }
        setPullNotificationCheckInterval(minutes);
    }

    protected void disablePushNotifications() {
        // Cancel the repeating call for "pull" notifications.
        JobManager.instance().cancelAllForTag(NotificationPullJobCreator.NOTIFICATIONS_JOB_TAG);
    }

    protected void setPullNotificationCheckInterval(long minutes) {
        long checkInterval = 1000 * 60 * minutes;

        new JobRequest.Builder(NotificationPullJobCreator.NOTIFICATIONS_JOB_TAG)
                .setPeriodic(checkInterval)
                .setUpdateCurrent(true)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .build()
                .scheduleAsync();
    }

    @Override
    protected void onDestroy() {
        for (Call call : callList) {
            call.cancel();
        }
        super.onDestroy();
    }
}
