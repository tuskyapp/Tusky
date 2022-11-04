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

import com.google.gson.annotations.SerializedName

data class Instance(
    val uri: String,
    // val title: String,
    // val description: String,
    // val email: String,
    val version: String,
    // val urls: Map<String, String>,
    // val stats: Map<String, Int>?,
    // val thumbnail: String?,
    // val languages: List<String>,
    // @SerializedName("contact_account") val contactAccount: Account,
    @SerializedName("max_toot_chars") val maxTootChars: Int?,
    @SerializedName("poll_limits") val pollConfiguration: PollConfiguration?,
    val configuration: InstanceConfiguration?,
    @SerializedName("max_media_attachments") val maxMediaAttachments: Int?,
    val pleroma: PleromaConfiguration?,
    @SerializedName("upload_limit") val uploadLimit: Int?,
    val rules: List<InstanceRules>?
) {
    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Instance) {
            return false
        }
        val instance = other as Instance?
        return instance?.uri.equals(uri)
    }
}

data class PollConfiguration(
    @SerializedName("max_options") val maxOptions: Int?,
    @SerializedName("max_option_chars") val maxOptionChars: Int?,
    @SerializedName("max_characters_per_option") val maxCharactersPerOption: Int?,
    @SerializedName("min_expiration") val minExpiration: Int?,
    @SerializedName("max_expiration") val maxExpiration: Int?,
)

data class InstanceConfiguration(
    val statuses: StatusConfiguration?,
    @SerializedName("media_attachments") val mediaAttachments: MediaAttachmentConfiguration?,
    val polls: PollConfiguration?,
)

data class StatusConfiguration(
    @SerializedName("max_characters") val maxCharacters: Int?,
    @SerializedName("max_media_attachments") val maxMediaAttachments: Int?,
    @SerializedName("characters_reserved_per_url") val charactersReservedPerUrl: Int?,
)

data class MediaAttachmentConfiguration(
    @SerializedName("supported_mime_types") val supportedMimeTypes: List<String>?,
    @SerializedName("image_size_limit") val imageSizeLimit: Int?,
    @SerializedName("image_matrix_limit") val imageMatrixLimit: Int?,
    @SerializedName("video_size_limit") val videoSizeLimit: Int?,
    @SerializedName("video_frame_rate_limit") val videoFrameRateLimit: Int?,
    @SerializedName("video_matrix_limit") val videoMatrixLimit: Int?,
)

data class PleromaConfiguration(
    val metadata: PleromaMetadata?
)

data class PleromaMetadata(
    @SerializedName("fields_limits") val fieldLimits: PleromaFieldLimits
)

data class PleromaFieldLimits(
    @SerializedName("max_fields") val maxFields: Int?,
    @SerializedName("name_length") val nameLength: Int?,
    @SerializedName("value_length") val valueLength: Int?
)

data class InstanceRules(
    val id: String,
    val text: String
)
