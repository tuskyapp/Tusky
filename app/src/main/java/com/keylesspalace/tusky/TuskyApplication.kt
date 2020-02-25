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
import androidx.emoji.text.EmojiCompat
import androidx.preference.PreferenceManager
import com.evernote.android.job.JobManager
import com.keylesspalace.tusky.di.AppInjector
import com.keylesspalace.tusky.util.EmojiCompatFont
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.NotificationPullJobCreator
import com.keylesspalace.tusky.util.ThemeUtils
import com.uber.autodispose.AutoDisposePlugins
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject

class TuskyApplication : Application(), HasAndroidInjector {

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>
    @Inject
    lateinit var notificationPullJobCreator: NotificationPullJobCreator

    override fun onCreate() {

        super.onCreate()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        AutoDisposePlugins.setHideProxies(false) // a small performance optimization

        AppInjector.init(this)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        // init the custom emoji fonts
        val emojiSelection = preferences.getInt(EmojiPreference.FONT_PREFERENCE, 0)
        val emojiConfig = EmojiCompatFont.byId(emojiSelection)
                .getConfig(this)
                .setReplaceAll(true)
        EmojiCompat.init(emojiConfig)

        // init night mode
        val theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT)
        ThemeUtils.setAppNightMode(theme)

        JobManager.create(this).addJobCreator(notificationPullJobCreator)
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