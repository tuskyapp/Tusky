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
import androidx.work.WorkManager
import autodispose2.AutoDisposePlugins
import com.keylesspalace.tusky.components.notifications.NotificationWorkerFactory
import com.keylesspalace.tusky.di.AppInjector
import com.keylesspalace.tusky.settings.PrefStore
import com.keylesspalace.tusky.settings.getBlocking
import com.keylesspalace.tusky.settings.makePrefStore
import com.keylesspalace.tusky.util.EmojiCompatFont
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.ThemeUtils
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject

class TuskyApplication : Application(), HasAndroidInjector {

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var notificationWorkerFactory: NotificationWorkerFactory

    @Inject
    lateinit var prefStore: PrefStore

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


        // init the custom emoji fonts
        val emojiSelection = prefStore.getBlocking().emojiFont
        val emojiConfig = EmojiCompatFont.byId(emojiSelection)
            .getConfig(this)
            .setReplaceAll(true)
        EmojiCompat.init(emojiConfig)

        // init night mode
        val theme = prefStore.getBlocking().appTheme
        ThemeUtils.setAppNightMode(theme)

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

    override fun attachBaseContext(base: Context) {
        // Special case: injected field cannot be injected here yet so we create Prefs by hand
        // Give it a blocking scope so that it will be closed and pref store will be released
        runBlocking {
            val prefs = makePrefStore(base, this)
            localeManager = LocaleManager(prefs)
            super.attachBaseContext(localeManager.setLocale(base))
        }
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
