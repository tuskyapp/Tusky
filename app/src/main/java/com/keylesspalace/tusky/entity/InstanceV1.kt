/* Copyright 2018 Levi Bard
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

package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InstanceV1(
    val uri: String,
    // val title: String,
    // val description: String,
    // val email: String,
    val version: String,
    // val urls: Map<String, String>,
    // val stats: Map<String, Int>?,
    // val thumbnail: String?,
    // val languages: List<String>,
    // @Json(name = "contact_account") val contactAccount: Account?,
    @Json(name = "max_toot_chars") val maxTootChars: Int? = null,
    @Json(name = "poll_limits") val pollConfiguration: PollConfiguration? = null,
    val configuration: InstanceConfiguration? = null,
    @Json(name = "max_media_attachments") val maxMediaAttachments: Int? = null,
    val pleroma: PleromaConfiguration? = null,
    @Json(name = "upload_limit") val uploadLimit: Int? = null,
    val rules: List<InstanceRules> = emptyList()
) {
    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is InstanceV1) {
            return false
        }
        return other.uri == uri
    }
}

@JsonClass(generateAdapter = true)
data class PollConfiguration(
    @Json(name = "max_options") val maxOptions: Int? = null,
    @Json(name = "max_option_chars") val maxOptionChars: Int? = null,
    @Json(name = "max_characters_per_option") val maxCharactersPerOption: Int? = null,
    @Json(name = "min_expiration") val minExpiration: Int? = null,
    @Json(name = "max_expiration") val maxExpiration: Int? = null
)

@JsonClass(generateAdapter = true)
data class InstanceConfiguration(
    val statuses: StatusConfiguration? = null,
    @Json(name = "media_attachments") val mediaAttachments: MediaAttachmentConfiguration? = null,
    val polls: PollConfiguration? = null
)

@JsonClass(generateAdapter = true)
data class StatusConfiguration(
    @Json(name = "max_characters") val maxCharacters: Int? = null,
    @Json(name = "max_media_attachments") val maxMediaAttachments: Int? = null,
    @Json(name = "characters_reserved_per_url") val charactersReservedPerUrl: Int? = null
)

@JsonClass(generateAdapter = true)
data class MediaAttachmentConfiguration(
    @Json(name = "supported_mime_types") val supportedMimeTypes: List<String> = emptyList(),
    @Json(name = "image_size_limit") val imageSizeLimit: Int? = null,
    @Json(name = "image_matrix_limit") val imageMatrixLimit: Int? = null,
    @Json(name = "video_size_limit") val videoSizeLimit: Int? = null,
    @Json(name = "video_frame_rate_limit") val videoFrameRateLimit: Int? = null,
    @Json(name = "video_matrix_limit") val videoMatrixLimit: Int? = null
)

@JsonClass(generateAdapter = true)
data class PleromaConfiguration(
    val metadata: PleromaMetadata? = null
)

@JsonClass(generateAdapter = true)
data class PleromaMetadata(
    @Json(name = "fields_limits") val fieldLimits: PleromaFieldLimits
)

@JsonClass(generateAdapter = true)
data class PleromaFieldLimits(
    @Json(name = "max_fields") val maxFields: Int? = null,
    @Json(name = "name_length") val nameLength: Int? = null,
    @Json(name = "value_length") val valueLength: Int? = null
)

@JsonClass(generateAdapter = true)
data class InstanceRules(
    val id: String,
    val text: String
)
