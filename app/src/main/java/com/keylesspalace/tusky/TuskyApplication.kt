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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import autodispose2.AutoDisposePlugins
import com.keylesspalace.tusky.components.notifications.NotificationWorkerFactory
import com.keylesspalace.tusky.di.AppInjector
import com.keylesspalace.tusky.service.SendStatusService
import com.keylesspalace.tusky.util.APP_THEME_DEFAULT
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.setAppNightMode
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import de.c1710.filemojicompat_defaults.DefaultEmojiPackList
import de.c1710.filemojicompat_ui.helpers.EmojiPackHelper
import de.c1710.filemojicompat_ui.helpers.EmojiPreference
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject

class TuskyApplication : Application(), HasAndroidInjector {

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var notificationWorkerFactory: NotificationWorkerFactory

    @Inject
    lateinit var localeManager: LocaleManager

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

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        // In this case, we want to have the emoji preferences merged with the other ones
        // Copied from PreferenceManager.getDefaultSharedPreferenceName
        EmojiPreference.sharedPreferenceName = packageName + "_preferences"
        EmojiPackHelper.init(this, DefaultEmojiPackList.get(this), allowPackImports = false)

        // init night mode
        val theme = preferences.getString("appTheme", APP_THEME_DEFAULT)
        setAppNightMode(theme)

        localeManager.setLocale()

        RxJavaPlugins.setErrorHandler {
            Log.w("RxJava", "undeliverable exception", it)
        }

        WorkManager.initialize(
            this,
            androidx.work.Configuration.Builder()
                .setWorkerFactory(notificationWorkerFactory)
                .build()
        )

        createNotificationChannels()
    }

    /**
     * Create app-global notification channels for sending and errors. This
     * ensures that the channels are available and configurable by the user
     * *before* a status is sent or an error occurs.
     */
    // See https://github.com/tuskyapp/Tusky/pull/3185#issuecomment-1399352266
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            val channel = NotificationChannel(
                SendStatusService.CHANNEL_ID,
                getString(R.string.send_post_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)

            val errorChannel = NotificationChannel(
                SendStatusService.CHANNEL_ID_ERROR,
                getString(R.string.notification_send_error_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            errorChannel.description = getString(R.string.notification_send_error_description)
            errorChannel.enableVibration(true)
            errorChannel.setShowBadge(true)
            notificationManager.createNotificationChannel(errorChannel)
        }
    }

    override fun androidInjector() = androidInjector
}
