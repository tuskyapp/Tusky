/* Copyright 2019 Tusky Contributors
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

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
data class NewStatus(
    val status: String,
    @Json(name = "spoiler_text") val warningText: String,
    @Json(name = "in_reply_to_id") val inReplyToId: String? = null,
    val visibility: String,
    val sensitive: Boolean,
    @Json(name = "media_ids") val mediaIds: List<String> = emptyList(),
    @Json(name = "media_attributes") val mediaAttributes: List<MediaAttribute> = emptyList(),
    @Json(name = "scheduled_at") val scheduledAt: String? = null,
    val poll: NewPoll? = null,
    val language: String? = null
)

@JsonClass(generateAdapter = true)
@Parcelize
data class NewPoll(
    val options: List<String>,
    @Json(name = "expires_in") val expiresIn: Int,
    val multiple: Boolean
) : Parcelable

// It would be nice if we could reuse MediaToSend,
// but the server requires a different format for focus
@JsonClass(generateAdapter = true)
@Parcelize
data class MediaAttribute(
    val id: String,
    val description: String? = null,
    val focus: String? = null,
    val thumbnail: String? = null
) : Parcelable
