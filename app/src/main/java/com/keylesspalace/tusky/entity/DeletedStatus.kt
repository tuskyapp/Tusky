/* Copyright 2019 Tusky contributors
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
import java.util.Date

@JsonClass(generateAdapter = true)
data class DeletedStatus(
    val text: String?,
    @Json(name = "in_reply_to_id") val inReplyToId: String? = null,
    @Json(name = "spoiler_text") val spoilerText: String,
    val visibility: Status.Visibility,
    val sensitive: Boolean,
    @Json(name = "media_attachments") val attachments: List<Attachment> = emptyList(),
    val poll: Poll? = null,
    @Json(name = "created_at") val createdAt: Date,
    val language: String? = null
) {
    val isEmpty: Boolean
        get() {
            return text == null && attachments.isEmpty()
        }
}
