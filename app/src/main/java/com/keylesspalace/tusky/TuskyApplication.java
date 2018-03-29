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
import android.app.UiModeManager;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatDelegate;

import com.evernote.android.job.JobManager;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.di.AppInjector;
import com.keylesspalace.tusky.util.OkHttpUtils;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.squareup.picasso.Picasso;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasServiceInjector;

public class TuskyApplication extends Application implements HasActivityInjector, HasServiceInjector {
    public static final String APP_THEME_DEFAULT = ThemeUtils.THEME_NIGHT;

    private static AppDatabase db;
    private AccountManager accountManager;
    @Inject
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector;
    @Inject
    DispatchingAndroidInjector<Service> dispatchingServiceInjector;
    @Inject
    NotificationPullJobCreator notificationPullJobCreator;

    public static AppDatabase getDB() {
        return db;
    }

    private static UiModeManager uiModeManager;

    public static UiModeManager getUiModeManager() {
        return uiModeManager;
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
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
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

        AppInjector.INSTANCE.init(this);
        initPicasso();

        JobManager.create(this).addJobCreator(notificationPullJobCreator);
        uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);

        //necessary for Android < APi 21
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    protected void initPicasso() {
        // Initialize Picasso configuration
        Picasso.Builder builder = new Picasso.Builder(this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        builder.downloader(new OkHttp3Downloader(OkHttpUtils.getCompatibleClient(preferences)));
        if (BuildConfig.DEBUG) {
            builder.listener((picasso, uri, exception) -> exception.printStackTrace());
        }
        try {
            Picasso.setSingletonInstance(builder.build());
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        }
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

    public interface ServiceLocator {
        <T> T get(Class<T> clazz);
    }
}