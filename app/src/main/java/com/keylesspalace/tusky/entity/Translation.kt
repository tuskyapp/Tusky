package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class MediaTranslation(
    val id: String,
    val description: String,
)

/**
 * Represents the result of machine translating some status content.
 *
 * See [doc](https://docs.joinmastodon.org/entities/Translation/).
 */
data class Translation(
    val content: String,
    @SerializedName("spoiler_warning")
    val spoilerWarning: String?,
    val poll: TranslatedPoll?,
    @SerializedName("media_attachments")
    val mediaAttachments: List<MediaTranslation>,
    @SerializedName("detected_source_language")
    val detectedSourceLanguage: String,
    val provider: String,
)

data class TranslatedPoll(
    val options: List<TranslatedPollOption>
)

data class TranslatedPollOption(
    val title: String
)
