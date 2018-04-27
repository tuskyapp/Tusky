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
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.text.emoji.EmojiCompat;
import android.support.v7.app.AppCompatDelegate;

import com.evernote.android.job.JobManager;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.di.AppInjector;
import com.keylesspalace.tusky.entity.EmojiCompatFont;
import com.squareup.picasso.Picasso;

import java.io.File;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasBroadcastReceiverInjector;
import dagger.android.HasServiceInjector;
import okhttp3.OkHttpClient;

public class TuskyApplication extends Application implements HasActivityInjector, HasServiceInjector, HasBroadcastReceiverInjector {
    private static AppDatabase db;
    private AccountManager accountManager;
    @Inject
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector;
    @Inject
    DispatchingAndroidInjector<Service> dispatchingServiceInjector;
    @Inject
    DispatchingAndroidInjector<BroadcastReceiver> dispatchingBroadcastReceiverInjector;
    @Inject
    NotificationPullJobCreator notificationPullJobCreator;
    @Inject OkHttpClient okHttpClient;

    public static AppDatabase getDB() {
        return db;
    }

    public static TuskyApplication getInstance(@NonNull Context context) {
        return (TuskyApplication) context.getApplicationContext();
    }

    private ServiceLocator serviceLocator;

    @Override
    public void onCreate() {
        super.onCreate();

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "tuskyDB")
                .allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
                .build();
        accountManager = new AccountManager(db);
        serviceLocator = new ServiceLocator() {
            @Override
            public <T> T get(Class<T> clazz) {
                if (clazz.equals(AccountManager.class)) {
                    //noinspection unchecked
                    return (T) accountManager;
                } else {
                    throw new IllegalArgumentException("Unknown service " + clazz);
                }
            }
        };

        initAppInjector();
        initPicasso();
        initEmojiCompat();

        JobManager.create(this).addJobCreator(notificationPullJobCreator);

        //necessary for Android < API 21
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    /**
     * This method will try to load the emoji font "EmojiCompat.ttf" which should be located at
     * [Internal Storage]/Android/com.keylesspalace.tusky/files/EmojiCompat.ttf.
     * If there is no font available it will use a dummy configuration to prevent crashing the app.
     */
    private void initEmojiCompat() {
        // Declaration
        EmojiCompat.Config config;
        // Load the font information
        String emojiPreference = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getString(EmojiPreference.FONT_PREFERENCE, "");
        EmojiCompatFont font = EmojiCompatFont.parseFont(emojiPreference);
        font.setBaseDirectory(new File(getApplicationContext().getExternalFilesDir(null), "emoji"));
        // FileEmojiCompat will handle any non-existing font and provide a fallback solution.
        config = font.getConfig(getApplicationContext())
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