/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import androidx.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import androidx.emoji.text.EmojiCompat;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.evernote.android.job.JobManager;
import com.jakewharton.picasso.OkHttp3Downloader;

import androidx.work.RxWorker;
import androidx.work.WorkManager;
import io.fabric.sdk.android.Fabric;
import tech.bigfig.roma.db.AccountManager;
import tech.bigfig.roma.db.AppDatabase;
import tech.bigfig.roma.di.AppInjector;
import tech.bigfig.roma.di.DaggerWorkerFactory;
import tech.bigfig.roma.di.HasWorkerInjector;
import tech.bigfig.roma.util.EmojiCompatFont;
import tech.bigfig.roma.util.NotificationPullJobCreator;

import tech.bigfig.roma.util.LocaleManager;
import com.squareup.picasso.Picasso;

import org.conscrypt.Conscrypt;
import org.jetbrains.annotations.NotNull;

import java.security.Security;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasBroadcastReceiverInjector;
import dagger.android.HasServiceInjector;
import okhttp3.OkHttpClient;

public class RomaApplication extends Application implements HasActivityInjector, HasServiceInjector, HasBroadcastReceiverInjector,
        HasWorkerInjector {
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
    @Inject
    DispatchingAndroidInjector<RxWorker> dispatchingWorkerInjector;

    private AppDatabase appDatabase;
    private AccountManager accountManager;

    private ServiceLocator serviceLocator;

    public static LocaleManager localeManager;

    @Override
    public void onCreate() {
        super.onCreate();
        initCrashlytics();

        initSecurityProvider();

        appDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "romaDB")
                .allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
                        AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11,
                        AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_10_13)
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
        initWorkManager();
        initPicasso();
        initEmojiCompat();

        JobManager.create(this).addJobCreator(notificationPullJobCreator);

    }
    private void initWorkManager(){
        androidx.work.Configuration config = new androidx.work.Configuration.Builder()
                .setWorkerFactory(new DaggerWorkerFactory()) // Overrides default WorkerFactory
                .build();
        WorkManager.initialize(this,config);
    }
    /**
     * Init crashlytics and disable crash reports for debug builds
     */
    private void initCrashlytics() {
        // Set up Crashlytics, disabled for debug builds
        Crashlytics crashlyticsKit = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                .build();

        // Initialize Fabric with the debug-disabled crashlytics.
        Fabric.with(this, crashlyticsKit);
    }

    protected void initSecurityProvider() {
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }

    @Override
    protected void attachBaseContext(Context base) {
        localeManager = new LocaleManager(base);
        super.attachBaseContext(localeManager.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        localeManager.setLocale(this);
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

    @NotNull
    @Override
    public AndroidInjector<RxWorker> workerInjector() {
        return dispatchingWorkerInjector;
    }


    public interface ServiceLocator {
        <T> T get(Class<T> clazz);
    }
}
