package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Instance(
    val domain: String,
//    val title: String,
    val version: String,
//    @Json(name = "source_url") val sourceUrl: String,
//    val description: String,
//    val usage: Usage,
//    val thumbnail: Thumbnail,
//    val languages: List<String>,
    val configuration: Configuration? = null,
//    val registrations: Registrations,
//    val contact: Contact,
    val rules: List<Rule> = emptyList(),
    val pleroma: PleromaConfiguration? = null,
    @Json(name = "api_versions") val apiVersions: ApiVersions? = null,
) {
    @JsonClass(generateAdapter = true)
    data class Usage(val users: Users) {
        @JsonClass(generateAdapter = true)
        data class Users(@Json(name = "active_month") val activeMonth: Int)
    }

    @JsonClass(generateAdapter = true)
    data class Thumbnail(
        val url: String,
        val blurhash: String? = null,
        val versions: Versions? = null
    ) {
        @JsonClass(generateAdapter = true)
        data class Versions(
            @Json(name = "@1x") val at1x: String? = null,
            @Json(name = "@2x") val at2x: String? = null
        )
    }

    @JsonClass(generateAdapter = true)
    data class Configuration(
        val urls: Urls? = null,
        val accounts: Accounts? = null,
        val statuses: Statuses? = null,
        @Json(name = "media_attachments") val mediaAttachments: MediaAttachments? = null,
        val polls: Polls? = null,
        val translation: Translation? = null,
    ) {
        @JsonClass(generateAdapter = true)
        data class Urls(@Json(name = "streaming_api") val streamingApi: String? = null)

        @JsonClass(generateAdapter = true)
        data class Accounts(
            @Json(name = "max_featured_tags") val maxFeaturedTags: Int,
            // GoToSocial feature
            @Json(name = "max_profile_fields") val maxProfileFields: Int?
        )

        @JsonClass(generateAdapter = true)
        data class Statuses(
            @Json(name = "max_characters") val maxCharacters: Int? = null,
            @Json(name = "max_media_attachments") val maxMediaAttachments: Int? = null,
            @Json(name = "characters_reserved_per_url") val charactersReservedPerUrl: Int? = null
        )

        @JsonClass(generateAdapter = true)
        data class MediaAttachments(
            // Warning: This is an array in mastodon and a dictionary in friendica
            // @Json(name = "supported_mime_types") val supportedMimeTypes: List<String> = emptyList(),
            @Json(name = "image_size_limit") val imageSizeLimitBytes: Long? = null,
            @Json(name = "image_matrix_limit") val imagePixelCountLimit: Long? = null,
            @Json(name = "video_size_limit") val videoSizeLimitBytes: Long? = null,
            @Json(name = "video_matrix_limit") val videoPixelCountLimit: Long? = null,
            @Json(name = "video_frame_rate_limit") val videoFrameRateLimit: Int? = null
        )

        @JsonClass(generateAdapter = true)
        data class Polls(
            @Json(name = "max_options") val maxOptions: Int? = null,
            @Json(name = "max_characters_per_option") val maxCharactersPerOption: Int? = null,
            @Json(name = "min_expiration") val minExpirationSeconds: Int? = null,
            @Json(name = "max_expiration") val maxExpirationSeconds: Int? = null
        )

        @JsonClass(generateAdapter = true)
        data class Translation(val enabled: Boolean)
    }

    @JsonClass(generateAdapter = true)
    data class Registrations(
        val enabled: Boolean,
        @Json(name = "approval_required") val approvalRequired: Boolean,
        val message: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class Contact(val email: String, val account: Account)

    @JsonClass(generateAdapter = true)
    data class Rule(val id: String, val text: String)

    @JsonClass(generateAdapter = true)
    data class ApiVersions(val mastodon: Int? = null)
}
