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

private fun LocaleListCompat.toList(): List<Locale> {
    val list = mutableListOf<Locale>()
    for (index in 0 until this.size()) {
        this[index]?.let { list.add(it) }
    }
    return list
}

// Ensure that the locale whose code matches the given language is first in the list
private fun ensureLanguagesAreFirst(locales: MutableList<Locale>, languages: List<String>) {
    for (language in languages.reversed()) {
        // Iterate prioritized languages in reverse to retain the order once bubbled to the top
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
                continue
            }
        }

        if (currentLocaleIndex > 0) {
            // Move preselected locale to the top
            locales.add(0, locales.removeAt(currentLocaleIndex))
        }
    }
}

fun getInitialLanguages(language: String? = null, activeAccount: AccountEntity? = null): List<String> {
    val selected = listOfNotNull(language, activeAccount?.defaultPostLanguage)
    val system = AppCompatDelegate.getApplicationLocales().toList() +
        LocaleListCompat.getDefault().toList()

    return (selected + system.map { it.language }).distinct().filter { it.isNotEmpty() }
}

fun getLocaleList(initialLanguages: List<String>): List<Locale> {
    val locales = Locale.getAvailableLocales().filter {
        // Only "base" languages, "en" but not "en_DK"
        it.country.isNullOrEmpty() &&
            it.script.isNullOrEmpty() &&
            it.variant.isNullOrEmpty()
    }.sortedBy { it.displayName }.toMutableList()
    ensureLanguagesAreFirst(locales, initialLanguages)
    return locales
}
