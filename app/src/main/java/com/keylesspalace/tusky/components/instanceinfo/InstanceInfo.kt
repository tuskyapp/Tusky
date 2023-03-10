/* Copyright 2022 Tusky contributors
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

package com.keylesspalace.tusky.components.instanceinfo

import com.keylesspalace.tusky.db.InstanceInfoEntity

data class InstanceInfo(
    val maxChars: Int,
    val pollMaxOptions: Int,
    val pollMaxLength: Int,
    val pollMinDuration: Int,
    val pollMaxDuration: Int,
    val charactersReservedPerUrl: Int,
    val videoSizeLimit: Int,
    val imageSizeLimit: Int,
    val imageMatrixLimit: Int,
    val maxMediaAttachments: Int,
    val maxFields: Int,
    val maxFieldNameLength: Int?,
    val maxFieldValueLength: Int?
) {

    companion object {
        const val DEFAULT_CHARACTER_LIMIT = 500
        private const val DEFAULT_MAX_OPTION_COUNT = 4
        private const val DEFAULT_MAX_OPTION_LENGTH = 50
        private const val DEFAULT_MIN_POLL_DURATION = 300
        private const val DEFAULT_MAX_POLL_DURATION = 604800

        private const val DEFAULT_VIDEO_SIZE_LIMIT = 41943040 // 40MiB
        private const val DEFAULT_IMAGE_SIZE_LIMIT = 10485760 // 10MiB
        private const val DEFAULT_IMAGE_MATRIX_LIMIT = 16777216 // 4096^2 Pixels

        // Mastodon only counts URLs as this long in terms of status character limits
        const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23

        const val DEFAULT_MAX_MEDIA_ATTACHMENTS = 4
        const val DEFAULT_MAX_ACCOUNT_FIELDS = 4

        fun default(): InstanceInfo {
            return InstanceInfo(
                maxChars = DEFAULT_CHARACTER_LIMIT,
                pollMaxOptions = DEFAULT_MAX_OPTION_COUNT,
                pollMaxLength = DEFAULT_MAX_OPTION_LENGTH,
                pollMinDuration = DEFAULT_MIN_POLL_DURATION,
                pollMaxDuration = DEFAULT_MAX_POLL_DURATION,
                charactersReservedPerUrl = DEFAULT_CHARACTERS_RESERVED_PER_URL,
                videoSizeLimit = DEFAULT_VIDEO_SIZE_LIMIT,
                imageSizeLimit = DEFAULT_IMAGE_SIZE_LIMIT,
                imageMatrixLimit = DEFAULT_IMAGE_MATRIX_LIMIT,
                maxMediaAttachments = DEFAULT_MAX_MEDIA_ATTACHMENTS,
                maxFields = DEFAULT_MAX_ACCOUNT_FIELDS,
                maxFieldNameLength = null,
                maxFieldValueLength = null
            )
        }

        fun from(instanceInfo: InstanceInfoEntity): InstanceInfo {
            return InstanceInfo(
                maxChars = instanceInfo.maximumTootCharacters ?: DEFAULT_CHARACTER_LIMIT,
                pollMaxOptions = instanceInfo.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
                pollMaxLength = instanceInfo.maxPollOptionLength ?: DEFAULT_MAX_OPTION_LENGTH,
                pollMinDuration = instanceInfo.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
                pollMaxDuration = instanceInfo.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
                charactersReservedPerUrl = instanceInfo.charactersReservedPerUrl ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
                videoSizeLimit = instanceInfo.videoSizeLimit ?: DEFAULT_VIDEO_SIZE_LIMIT,
                imageSizeLimit = instanceInfo.imageSizeLimit ?: DEFAULT_IMAGE_SIZE_LIMIT,
                imageMatrixLimit = instanceInfo.imageMatrixLimit ?: DEFAULT_IMAGE_MATRIX_LIMIT,
                maxMediaAttachments = instanceInfo.maxMediaAttachments ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
                maxFields = instanceInfo.maxFields ?: DEFAULT_MAX_ACCOUNT_FIELDS,
                maxFieldNameLength = instanceInfo.maxFieldNameLength,
                maxFieldValueLength = instanceInfo.maxFieldValueLength
            )
        }
    }
}
