package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class TranslationResult(
    val content: String,
    @SerializedName("detected_source_language") val detectedSourceLanguage: String,
    val provider: String
) {

    val displayedContent: String
        get() {
            // TODO: Translate this text & do this properly
            // TODO: detectedSourceLanguage is short, map it to full name
            return "$content\n\n<small>Translated from $detectedSourceLanguage with $provider</small>"
        }

}
