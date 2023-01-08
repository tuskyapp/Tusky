/* Copyright 2018 Conny Duck
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

package com.keylesspalace.tusky.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Instance

@Entity
@TypeConverters(Converters::class)
data class InstanceEntity(
    @PrimaryKey val instance: String,
    val emojiList: List<Emoji>?,
    val maximumTootCharacters: Int?,
    val maxPollOptions: Int?,
    val maxPollOptionLength: Int?,
    val minPollDuration: Int?,
    val maxPollDuration: Int?,
    val charactersReservedPerUrl: Int?,
    val version: String?,
    val videoSizeLimit: Int?,
    val imageSizeLimit: Int?,
    val imageMatrixLimit: Int?,
    val maxMediaAttachments: Int?,
    val maxFields: Int?,
    val maxFieldNameLength: Int?,
    val maxFieldValueLength: Int?
)

@TypeConverters(Converters::class)
data class EmojisEntity(
    @PrimaryKey val instance: String,
    val emojiList: List<Emoji>?
)

data class InstanceInfoEntity(
    @PrimaryKey val instance: String,
    val maximumTootCharacters: Int?,
    val maxPollOptions: Int?,
    val maxPollOptionLength: Int?,
    val minPollDuration: Int?,
    val maxPollDuration: Int?,
    val charactersReservedPerUrl: Int?,
    val version: String?,
    val videoSizeLimit: Int?,
    val imageSizeLimit: Int?,
    val imageMatrixLimit: Int?,
    val maxMediaAttachments: Int?,
    val maxFields: Int?,
    val maxFieldNameLength: Int?,
    val maxFieldValueLength: Int?
) {

    companion object {
        fun from(instance: Instance, instanceName: String): InstanceInfoEntity {
            return InstanceInfoEntity(
                instance = instanceName,
                maximumTootCharacters = instance.configuration?.statuses?.maxCharacters ?: instance.maxTootChars,
                maxPollOptions = instance.configuration?.polls?.maxOptions ?: instance.pollConfiguration?.maxOptions,
                maxPollOptionLength = instance.configuration?.polls?.maxCharactersPerOption ?: instance.pollConfiguration?.maxOptionChars,
                minPollDuration = instance.configuration?.polls?.minExpiration ?: instance.pollConfiguration?.minExpiration,
                maxPollDuration = instance.configuration?.polls?.maxExpiration ?: instance.pollConfiguration?.maxExpiration,
                charactersReservedPerUrl = instance.configuration?.statuses?.charactersReservedPerUrl,
                version = instance.version,
                videoSizeLimit = instance.configuration?.mediaAttachments?.videoSizeLimit ?: instance.uploadLimit,
                imageSizeLimit = instance.configuration?.mediaAttachments?.imageSizeLimit ?: instance.uploadLimit,
                imageMatrixLimit = instance.configuration?.mediaAttachments?.imageMatrixLimit,
                maxMediaAttachments = instance.configuration?.statuses?.maxMediaAttachments ?: instance.maxMediaAttachments,
                maxFields = instance.pleroma?.metadata?.fieldLimits?.maxFields,
                maxFieldNameLength = instance.pleroma?.metadata?.fieldLimits?.nameLength,
                maxFieldValueLength = instance.pleroma?.metadata?.fieldLimits?.valueLength
            )
        }
    }
}
