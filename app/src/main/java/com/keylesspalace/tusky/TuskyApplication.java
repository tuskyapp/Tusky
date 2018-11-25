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

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.preference.PreferenceManager;
import android.support.text.emoji.EmojiCompat;

import com.evernote.android.job.JobManager;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.di.AppInjector;
import com.keylesspalace.tusky.util.EmojiCompatFont;
import com.keylesspalace.tusky.util.NotificationPullJobCreator;
import com.squareup.picasso.Picasso;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasBroadcastReceiverInjector;
import dagger.android.HasServiceInjector;
import okhttp3.OkHttpClient;

public class TuskyApplication extends Application implements HasActivityInjector, HasServiceInjector, HasBroadcastReceiverInjector {
    @Inject
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector;
    @Inject
    DispatchingAndroidInjector<Service> dispatchingServiceInjector;
    @Inject
    DispatchingAndroidInjector<BroadcastReceiver> dispatchingBroadcastReceiverInjector;
    @Inject
    NotificationPullJobCreator notificationPullJobCreator;
    @Inject
    OkHttpClient okHttpClient;

    private AppDatabase appDatabase;
    private AccountManager accountManager;

    private ServiceLocator serviceLocator;

    @Override
    public void onCreate() {
        super.onCreate();

        appDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "tuskyDB")
                .allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
                        AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
                .build();
        accountManager = new AccountManager(appDatabase);
        serviceLocator = new ServiceLocator() {
            @Override
            public <T> T get(Class<T> clazz) {
                if (clazz.equals(AccountManager.class)) {
                    //noinspection unchecked
                    return (T) accountManager;
                } else if (clazz.equals(AppDatabase.class)) {
                    //noinspection unchecked
                    return (T) appDatabase;
                } else {
                    throw new IllegalArgumentException("Unknown service " + clazz);
                }
            }
        };

        initAppInjector();
        initPicasso();
        initEmojiCompat();

        JobManager.create(this).addJobCreator(notificationPullJobCreator);

    }

    /**
     * This method will load the EmojiCompat font which has been selected.
     * If this font does not work or if the user hasn't selected one (yet), it will use a
     * fallback solution instead which won't make any visible difference to using no EmojiCompat at all.
     */
    private void initEmojiCompat() {
        int emojiSelection = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext())
                .getInt(EmojiPreference.FONT_PREFERENCE, 0);
        EmojiCompatFont font = EmojiCompatFont.byId(emojiSelection);
        // FileEmojiCompat will handle any non-existing font and provide a fallback solution.
        EmojiCompat.Config config = font.getConfig(getApplicationContext())
                // The user probably wants to get a consistent experience
                .setReplaceAll(true);
        EmojiCompat.init(config);
    }

    protected void initAppInjector() {
        AppInjector.INSTANCE.init(this);
    }

    protected void initPicasso() {
        // Initialize Picasso configuration
        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(okHttpClient));
        if (BuildConfig.DEBUG) {
            builder.listener((picasso, uri, exception) -> exception.printStackTrace());
        }

        Picasso.setSingletonInstance(builder.build());
    }

    public ServiceLocator getServiceLocator() {
        return serviceLocator;
    }

    @Override
    public AndroidInjector<Activity> activityInjector() {
        return dispatchingAndroidInjector;
    }

    @Override
    public AndroidInjector<Service> serviceInjector() {
        return dispatchingServiceInjector;
    }

    @Override
    public AndroidInjector<BroadcastReceiver> broadcastReceiverInjector() {
        return dispatchingBroadcastReceiverInjector;
    }

    public interface ServiceLocator {
        <T> T get(Class<T> clazz);
    }
}