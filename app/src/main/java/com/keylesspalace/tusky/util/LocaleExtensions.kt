package com.keylesspalace.tusky.util

import java.util.Locale

// When a language code has changed, `language` *explicitly* returns the obsolete version,
// but `toLanguageTag()` uses the current version
// https://developer.android.com/reference/java/util/Locale#getLanguage()
val Locale.modernLanguageCode: String
    get() {
        return this.toLanguageTag().split('-', limit = 2)[0]
    }
