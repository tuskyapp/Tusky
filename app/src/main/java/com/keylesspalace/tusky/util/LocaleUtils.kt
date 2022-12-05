/* Copyright 2022 Tusky Contributors
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

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.keylesspalace.tusky.db.AccountEntity
import java.util.Locale

private const val TAG: String = "LocaleUtils"

private fun mergeLocaleListCompat(list: MutableList<Locale>, localeListCompat: LocaleListCompat) {
    for (index in 0 until localeListCompat.size()) {
        val locale = localeListCompat[index]
        if (locale != null && list.none { locale.language == it.language }) {
            list.add(locale)
        }
    }
}

// Ensure that the locale whose code matches the given language is first in the list
private fun ensureLanguageIsFirst(locales: MutableList<Locale>, language: String) {
    var currentLocaleIndex = locales.indexOfFirst { it.language == language }
    if (currentLocaleIndex < 0) {
        // Recheck against modern language codes
        // This should only happen when replying or when the per-account post language is set
        // to a modern code
        currentLocaleIndex = locales.indexOfFirst { it.modernLanguageCode == language }

        if (currentLocaleIndex < 0) {
            // This can happen when:
            // - Your per-account posting language is set to one android doesn't know (e.g. toki pona)
            // - Replying to a post in a language android doesn't know
            locales.add(0, Locale(language))
            Log.w(TAG, "Attempting to use unknown language tag '$language'")
            return
        }
    }

    if (currentLocaleIndex > 0) {
        // Move preselected locale to the top
        locales.add(0, locales.removeAt(currentLocaleIndex))
    }
}

fun getInitialLanguage(language: String? = null, activeAccount: AccountEntity? = null): String {
    return if (language.isNullOrEmpty()) {
        // Account-specific language set on the server
        if (activeAccount?.defaultPostLanguage?.isNotEmpty() == true) {
            activeAccount.defaultPostLanguage
        } else {
            // Setting the application ui preference sets the default locale
            AppCompatDelegate.getApplicationLocales()[0]?.language
                ?: Locale.getDefault().language
        }
    } else {
        language
    }
}

fun getLocaleList(initialLanguage: String): List<Locale> {
    val locales = mutableListOf<Locale>()
    mergeLocaleListCompat(locales, AppCompatDelegate.getApplicationLocales()) // configured app languages first
    mergeLocaleListCompat(locales, LocaleListCompat.getDefault()) // then configured system languages
    locales.addAll( // finally, other languages
        // Only "base" languages, "en" but not "en_DK"
        Locale.getAvailableLocales().filter {
            it.country.isNullOrEmpty() &&
                it.script.isNullOrEmpty() &&
                it.variant.isNullOrEmpty()
        }.sortedBy { it.displayName }
    )
    ensureLanguageIsFirst(locales, initialLanguage)
    return locales
}
