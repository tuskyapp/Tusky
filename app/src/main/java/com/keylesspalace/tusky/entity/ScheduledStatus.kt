/* Copyright 2019 kyori19
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
data class ScheduledStatus(
    val id: String,
    @Json(name = "scheduled_at") val scheduledAt: String,
    val params: StatusParams,
    @Json(name = "media_attachments") val mediaAttachments: List<Attachment>
)

// minimal class to avoid json parsing errors with servers that don't support scheduling
// https://github.com/tuskyapp/Tusky/issues/4703
@JsonClass(generateAdapter = true)
data class ScheduledStatusReply(
    val id: String,
)
