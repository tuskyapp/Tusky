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
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import autodispose2.AutoDisposePlugins
import com.keylesspalace.tusky.components.notifications.NotificationWorkerFactory
import com.keylesspalace.tusky.di.AppInjector
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.ThemeUtils
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
        val theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT)
        ThemeUtils.setAppNightMode(theme)

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
    }

    override fun androidInjector() = androidInjector
}
