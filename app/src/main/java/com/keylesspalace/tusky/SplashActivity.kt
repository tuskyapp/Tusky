/* Copyright 2018 Conny Duck
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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.ThemeUtils
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity(), Injectable {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // properly theme the splashscreen
        if (Build.VERSION.SDK_INT >= 31) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            when (preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT)) {
                ThemeUtils.THEME_BLACK -> {
                    splashScreen.setSplashScreenTheme(R.style.SplashBlackTheme)
                }
                ThemeUtils.THEME_MATERIAL_YOU_DARK -> {
                    splashScreen.setSplashScreenTheme(R.style.SplashMaterialYouDarkTheme)
                }
                ThemeUtils.THEME_MATERIAL_YOU_LIGHT -> {
                    splashScreen.setSplashScreenTheme(R.style.SplashMaterialYouLightTheme)
                }
            }
        }

        super.onCreate(savedInstanceState)

        /** Determine whether the user is currently logged in, and if so go ahead and load the
         *  timeline. Otherwise, start the activity_login screen. */
        val intent = if (accountManager.activeAccount != null) {
            Intent(this, MainActivity::class.java)
        } else {
            LoginActivity.getIntent(this, LoginActivity.MODE_DEFAULT)
        }
        startActivity(intent)
        finish()
    }
}
