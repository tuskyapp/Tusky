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
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.NEW_INSTALL_SCHEMA_VERSION
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.PrefKeys.APP_THEME
import com.keylesspalace.tusky.settings.SCHEMA_VERSION
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.setAppNightMode
import com.keylesspalace.tusky.worker.PruneCacheWorker
import dagger.hilt.android.HiltAndroidApp
import de.c1710.filemojicompat_defaults.DefaultEmojiPackList
import de.c1710.filemojicompat_ui.helpers.EmojiPackHelper
import de.c1710.filemojicompat_ui.helpers.EmojiPreference
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.conscrypt.Conscrypt

@HiltAndroidApp
class TuskyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var localeManager: LocaleManager

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var notificationManager: NotificationManager

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

        val workManager = WorkManager.getInstance(this)

        // Migrate shared preference keys and defaults from version to version.
        val oldVersion = preferences.getInt(
            PrefKeys.SCHEMA_VERSION,
            NEW_INSTALL_SCHEMA_VERSION
        )
        if (oldVersion != SCHEMA_VERSION) {
            if (oldVersion < 2025021701) {
                // A new periodic work request is enqueued by unique name (and not tag anymore): stop the old one
                workManager.cancelAllWorkByTag("pullNotifications")
            }
            if (oldVersion < 2025022001 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // delete old now unused notification channels
                for (channel in notificationManager.notificationChannels) {
                    if (channel.id.startsWith("CHANNEL_SIGN_UP") || channel.id.startsWith("CHANNEL_REPORT")) {
                        notificationManager.deleteNotificationChannel(channel.id)
                    }
                }
            }

            upgradeSharedPreferences(oldVersion, SCHEMA_VERSION)
        }

        // In this case, we want to have the emoji preferences merged with the other ones
        // Copied from PreferenceManager.getDefaultSharedPreferenceName
        EmojiPreference.sharedPreferenceName = packageName + "_preferences"
        EmojiPackHelper.init(this, DefaultEmojiPackList.get(this), allowPackImports = false)

        // init night mode
        val theme = preferences.getString(APP_THEME, AppTheme.DEFAULT.value)
        setAppNightMode(theme)

        localeManager.setLocale()

        // Prune the database every ~ 12 hours when the device is idle.
        val pruneCacheWorker = PeriodicWorkRequestBuilder<PruneCacheWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PruneCacheWorker.PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            pruneCacheWorker
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun upgradeSharedPreferences(oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Upgrading shared preferences: $oldVersion -> $newVersion")
        val editor = preferences.edit()

        if (oldVersion < 2023022701) {
            // These preferences are (now) handled in AccountPreferenceHandler. Remove them from shared for clarity.

            editor.remove(PrefKeys.ALWAYS_OPEN_SPOILER)
            editor.remove(PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA)
            editor.remove(PrefKeys.MEDIA_PREVIEW_ENABLED)
        }

        if (oldVersion != NEW_INSTALL_SCHEMA_VERSION && oldVersion < 2023082301) {
            // Default value for appTheme is now THEME_SYSTEM. If the user is upgrading and
            // didn't have an explicit preference set use the previous default, so the
            // theme does not unexpectedly change.
            if (!preferences.contains(APP_THEME)) {
                editor.putString(APP_THEME, AppTheme.NIGHT.value)
            }
        }

        if (oldVersion < 2023112001) {
            editor.remove(PrefKeys.TAB_FILTER_HOME_REPLIES)
            editor.remove(PrefKeys.TAB_FILTER_HOME_BOOSTS)
            editor.remove(PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS)
        }

        if (oldVersion < 2024060201) {
            editor.remove(PrefKeys.Deprecated.FAB_HIDE)
        }

        editor.putInt(PrefKeys.SCHEMA_VERSION, newVersion)
        editor.apply()
    }

    companion object {
        private const val TAG = "TuskyApplication"
    }
}
