/* Copyright 2019 Mélanie Chauvel (ariasuni)
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

package com.keylesspalace.tusky.util

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.keylesspalace.tusky.settings.PrefStore
import com.keylesspalace.tusky.settings.getBlocking
import java.util.*

class LocaleManager(
    private val prefs: PrefStore,
) {

    fun setLocale(context: Context): Context {
        val language = prefs.getBlocking().language
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
