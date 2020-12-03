package com.keylesspalace.tusky.util

import com.google.gson.annotations.SerializedName

data class BuiltinEmoji(
        val emoji: String,
        val description: String,
        val category: String,
        val aliases: List<String>,
        val tags: List<String>,
        @SerializedName("unicode_version")
        val unicodeVersion: String,
        @SerializedName("ios_version")
        val iosVersion: String
) {
}