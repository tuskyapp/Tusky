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
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.emoji.text.EmojiCompat
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.keylesspalace.tusky.components.notifications.NotificationWorkerFactory
import com.keylesspalace.tusky.di.AppInjector
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.EmojiCompatFont
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.ThemeUtils
import com.uber.autodispose.AutoDisposePlugins
import dagger.Lazy
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject

class TuskyApplication : Application(), HasAndroidInjector {

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var notificationWorkerFactory: Lazy<NotificationWorkerFactory>

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

        // init the custom emoji fonts
        val emojiSelection = preferences.getInt(PrefKeys.EMOJI, 0)
        val emojiConfig = EmojiCompatFont.byId(emojiSelection)
                .getConfig(this)
                .setReplaceAll(true)
        EmojiCompat.init(emojiConfig)

        // init night mode
        val theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT)
        ThemeUtils.setAppNightMode(theme)

        RxJavaPlugins.setErrorHandler {
            Log.w("RxJava", "undeliverable exception", it)
        }

        // This will initialize the whole network stack and cache so we don't wan to wait for it
        Schedulers.computation().scheduleDirect {
            WorkManager.initialize(
                    this,
                    androidx.work.Configuration.Builder()
                            .setWorkerFactory(notificationWorkerFactory.get())
                            .build()
            )
        }
    }

    override fun attachBaseContext(base: Context) {
        localeManager = LocaleManager(base)
        super.attachBaseContext(localeManager.setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localeManager.setLocale(this)
    }

    override fun androidInjector() = androidInjector

    companion object {
        @JvmStatic
        lateinit var localeManager: LocaleManager
    }
}