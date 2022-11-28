package com.keylesspalace.tusky.util

import android.content.Context
import com.keylesspalace.tusky.R
import java.util.Locale

// When a language code has changed, `language` *explicitly* returns the obsolete version,
// but `toLanguageTag()` uses the current version
// https://developer.android.com/reference/java/util/Locale#getLanguage()
val Locale.modernLanguageCode: String
    get() {
        return this.toLanguageTag().split('-', limit = 2)[0]
    }

fun Locale.getTuskyDisplayName(context: Context): String {
    return context.getString(
        R.string.language_display_name_format,
        this?.displayLanguage,
        this?.getDisplayLanguage(this)
    )
}
