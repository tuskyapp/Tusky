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

import android.app.Application;
import android.arch.persistence.room.Room;
import android.net.Uri;

import com.evernote.android.job.JobManager;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.util.OkHttpUtils;
import com.squareup.picasso.Picasso;

public class TuskyApplication extends Application {
    private static AppDatabase db;

    public static AppDatabase getDB() {
        return db;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Picasso configuration
        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(OkHttpUtils.getCompatibleClient()));
        if (BuildConfig.DEBUG) {
            builder.loggingEnabled(true);
            builder.listener(new Picasso.Listener() {
                @Override
                public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                    exception.printStackTrace();
                }
            });
        }

        try {
            Picasso.setSingletonInstance(builder.build());
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        }

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "tuskyDB")
                .allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .build();

        JobManager.create(this).addJobCreator(new NotificationPullJobCreator(this));
    }
}