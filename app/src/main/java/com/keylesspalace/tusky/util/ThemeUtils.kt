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
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.use
import com.google.android.material.color.MaterialColors

/**
 * Provides runtime compatibility to obtain theme information and re-theme views, especially where
 * the ability to do so is not supported in resource files.
 */

const val THEME_NIGHT = "night"
const val THEME_DAY = "day"
const val THEME_BLACK = "black"
const val THEME_AUTO = "auto"
const val THEME_SYSTEM = "auto_system"
const val THEME_SYSTEM_BLACK = "auto_system_black"
const val APP_THEME_DEFAULT = THEME_SYSTEM

fun getDimension(context: Context, @AttrRes attribute: Int): Int {
    return context.obtainStyledAttributes(intArrayOf(attribute)).use { array ->
        array.getDimensionPixelSize(0, -1)
    }
}

fun setDrawableTint(context: Context, drawable: Drawable, @AttrRes attribute: Int) {
    drawable.setColorFilter(
        MaterialColors.getColor(context, attribute, Color.BLACK),
        PorterDuff.Mode.SRC_IN
    )
}

fun setAppNightMode(flavor: String?) {
    when (flavor) {
        THEME_NIGHT, THEME_BLACK -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_YES
        )
        THEME_DAY -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        THEME_AUTO -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_AUTO_TIME
        )
        THEME_SYSTEM, THEME_SYSTEM_BLACK -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}

fun isBlack(config: Configuration, theme: String?): Boolean {
    return when (theme) {
        THEME_BLACK -> true
        THEME_SYSTEM_BLACK -> when (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        else -> false
    }
}
