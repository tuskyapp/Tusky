/* Copyright 2019 Mélanie Chauvel (ariasuni)
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.preference.PreferenceManager
import tech.bigfig.roma.util.getNonNullString

import java.util.Locale

class LocaleManager(context: Context) {

    private var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun setLocale(context: Context): Context {
        val language = prefs.getNonNullString("language", "default")
        if (language == "default") {
            return context
        }
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
