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
@file:JvmName("ThemeUtils")

package com.keylesspalace.tusky.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.settings.AppTheme

/**
 * Provides runtime compatibility to obtain theme information and re-theme views, especially where
 * the ability to do so is not supported in resource files.
 */

fun setDrawableTint(context: Context, drawable: Drawable, @AttrRes attribute: Int) {
    drawable.setTint(MaterialColors.getColor(context, attribute, Color.BLACK))
}

fun setAppNightMode(flavor: String?) {
    when (flavor) {
        AppTheme.NIGHT.value, AppTheme.BLACK.value -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_YES
        )
        AppTheme.DAY.value -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        AppTheme.AUTO.value -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_AUTO_TIME
        )
        AppTheme.AUTO_SYSTEM.value, AppTheme.AUTO_SYSTEM_BLACK.value -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}

fun isBlack(config: Configuration, theme: String?): Boolean {
    return when (theme) {
        AppTheme.BLACK.value -> true
        AppTheme.AUTO_SYSTEM_BLACK.value -> when (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        else -> false
    }
}
