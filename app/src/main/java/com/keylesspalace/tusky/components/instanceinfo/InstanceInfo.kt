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
    val maxFieldValueLength: Int?,
    val version: String?,
    val translationEnabled: Boolean?,
    val mastodonApiVersion: Int?,
)
