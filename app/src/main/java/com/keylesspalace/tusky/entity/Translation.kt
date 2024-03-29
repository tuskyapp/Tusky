package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MediaTranslation(
    val id: String,
    val description: String,
)

/**
 * Represents the result of machine translating some status content.
 *
 * See [doc](https://docs.joinmastodon.org/entities/Translation/).
 */
@JsonClass(generateAdapter = true)
data class Translation(
    val content: String,
    @Json(name = "spoiler_warning")
    val spoilerWarning: String? = null,
    val poll: List<String>? = null,
    @Json(name = "media_attachments")
    val mediaAttachments: List<MediaTranslation>,
    @Json(name = "detected_source_language")
    val detectedSourceLanguage: String,
    val provider: String,
)
