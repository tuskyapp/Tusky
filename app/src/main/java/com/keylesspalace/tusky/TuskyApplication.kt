/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import autodispose2.AutoDisposePlugins
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.di.AppInjector
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.NEW_INSTALL_SCHEMA_VERSION
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.PrefKeys.APP_THEME
import com.keylesspalace.tusky.settings.SCHEMA_VERSION
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.setAppNightMode
import com.keylesspalace.tusky.worker.PruneCacheWorker
import com.keylesspalace.tusky.worker.WorkerFactory
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import de.c1710.filemojicompat_defaults.DefaultEmojiPackList
import de.c1710.filemojicompat_ui.helpers.EmojiPackHelper
import de.c1710.filemojicompat_ui.helpers.EmojiPreference
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.conscrypt.Conscrypt
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TuskyApplication : Application(), HasAndroidInjector {
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var workerFactory: WorkerFactory

    @Inject
    lateinit var localeManager: LocaleManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        // Uncomment me to get StrictMode violation logs
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
//                    .detectDiskReads()
//                    .detectDiskWrites()
//                    .detectNetwork()
//                    .detectUnbufferedIo()
//                    .penaltyLog()
//                    .build())
//        }
        super.onCreate()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        AutoDisposePlugins.setHideProxies(false) // a small performance optimization

        AppInjector.init(this)

        // Migrate shared preference keys and defaults from version to version.
        val oldVersion = sharedPreferences.getInt(PrefKeys.SCHEMA_VERSION, NEW_INSTALL_SCHEMA_VERSION)
        if (oldVersion != SCHEMA_VERSION) {
            upgradeSharedPreferences(oldVersion, SCHEMA_VERSION)
        }

        // In this case, we want to have the emoji preferences merged with the other ones
        // Copied from PreferenceManager.getDefaultSharedPreferenceName
        EmojiPreference.sharedPreferenceName = packageName + "_preferences"
        EmojiPackHelper.init(this, DefaultEmojiPackList.get(this), allowPackImports = false)

        // init night mode
        val theme = sharedPreferences.getString(APP_THEME, AppTheme.DEFAULT.value)
        setAppNightMode(theme)

        localeManager.setLocale()

        RxJavaPlugins.setErrorHandler {
            Log.w("RxJava", "undeliverable exception", it)
        }

        NotificationHelper.createWorkerNotificationChannel(this)

        WorkManager.initialize(
            this,
            androidx.work.Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        )

        // Prune the database every ~ 12 hours when the device is idle.
        val pruneCacheWorker = PeriodicWorkRequestBuilder<PruneCacheWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PruneCacheWorker.PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            pruneCacheWorker
        )
    }

    override fun androidInjector() = androidInjector

    private fun upgradeSharedPreferences(oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Upgrading shared preferences: $oldVersion -> $newVersion")
        val editor = sharedPreferences.edit()

        if (oldVersion < 2023022701) {
            // These preferences are (now) handled in AccountPreferenceHandler. Remove them from shared for clarity.

            editor.remove(PrefKeys.ALWAYS_OPEN_SPOILER)
            editor.remove(PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA)
            editor.remove(PrefKeys.MEDIA_PREVIEW_ENABLED)
        }

        if (oldVersion < 2023072401) {
            // The notifications filter / clear options are shown on a menu, not a separate bar,
            // the preference to display them is not needed.
            editor.remove(PrefKeys.Deprecated.SHOW_NOTIFICATIONS_FILTER)
        }

        if (oldVersion != NEW_INSTALL_SCHEMA_VERSION && oldVersion < 2023082301) {
            // Default value for appTheme is now THEME_SYSTEM. If the user is upgrading and
            // didn't have an explicit preference set use the previous default, so the
            // theme does not unexpectedly change.
            if (!sharedPreferences.contains(APP_THEME)) {
                editor.putString(APP_THEME, AppTheme.NIGHT.value)
            }
        }

        if (oldVersion < 2023112001) {
            editor.remove(PrefKeys.TAB_FILTER_HOME_REPLIES)
            editor.remove(PrefKeys.TAB_FILTER_HOME_BOOSTS)
            editor.remove(PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS)
        }

        editor.putInt(PrefKeys.SCHEMA_VERSION, newVersion)
        editor.apply()
    }

    companion object {
        private const val TAG = "TuskyApplication"
    }
}
