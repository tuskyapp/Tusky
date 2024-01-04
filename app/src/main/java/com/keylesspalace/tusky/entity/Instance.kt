package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class Instance(
    val domain: String,
//    val title: String,
    val version: String,
//    @SerializedName("source_url") val sourceUrl: String,
//    val description: String,
//    val usage: Usage,
//    val thumbnail: Thumbnail,
//    val languages: List<String>,
    val configuration: Configuration,
//    val registrations: Registrations,
//    val contact: Contact,
    val rules: List<Rule>,
    val pleroma: PleromaConfiguration?,
) {
    data class Usage(val users: Users) {
        data class Users(@SerializedName("active_month") val activeMonth: Int)
    }
    data class Thumbnail(
        val url: String,
        val blurhash: String?,
        val versions: Versions?,
    ) {
        data class Versions(
            @SerializedName("@1x") val at1x: String?,
            @SerializedName("@2x") val at2x: String?,
        )
    }
    data class Configuration(
        val urls: Urls?,
        val accounts: Accounts?,
        val statuses: Statuses?,
        @SerializedName("media_attachments") val mediaAttachments: MediaAttachments?,
        val polls: Polls?,
        val translation: Translation?,
    ) {
        data class Urls(@SerializedName("streaming_api") val streamingApi: String)
        data class Accounts(@SerializedName("max_featured_tags") val maxFeaturedTags: Int)
        data class Statuses(
            @SerializedName("max_characters") val maxCharacters: Int,
            @SerializedName("max_media_attachments") val maxMediaAttachments: Int,
            @SerializedName("characters_reserved_per_url") val charactersReservedPerUrl: Int,
        )
        data class MediaAttachments(
            // Warning: This is an array in mastodon and a dictionary in friendica
            // @SerializedName("supported_mime_types") val supportedMimeTypes: List<String>,
            @SerializedName("image_size_limit") val imageSizeLimitBytes: Long,
            @SerializedName("image_matrix_limit") val imagePixelCountLimit: Long,
            @SerializedName("video_size_limit") val videoSizeLimitBytes: Long,
            @SerializedName("video_matrix_limit") val videoPixelCountLimit: Long,
            @SerializedName("video_frame_rate_limit") val videoFrameRateLimit: Int,
        )
        data class Polls(
            @SerializedName("max_options") val maxOptions: Int,
            @SerializedName("max_characters_per_option") val maxCharactersPerOption: Int,
            @SerializedName("min_expiration") val minExpirationSeconds: Int,
            @SerializedName("max_expiration") val maxExpirationSeconds: Int,
        )
        data class Translation(val enabled: Boolean)
    }
    data class Registrations(
        val enabled: Boolean,
        @SerializedName("approval_required") val approvalRequired: Boolean,
        val message: String?,
    )
    data class Contact(val email: String, val account: Account)
    data class Rule(val id: String, val text: String)
}
