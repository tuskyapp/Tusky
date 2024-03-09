/* Copyright 2020 Tusky Contributors
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
data class Announcement(
    val id: String,
    val content: String,
    @Json(name = "starts_at") val startsAt: Date? = null,
    @Json(name = "ends_at") val endsAt: Date? = null,
    @Json(name = "all_day") val allDay: Boolean,
    @Json(name = "published_at") val publishedAt: Date,
    @Json(name = "updated_at") val updatedAt: Date,
    val read: Boolean,
    val mentions: List<Status.Mention>,
    val statuses: List<Status>,
    val tags: List<HashTag>,
    val emojis: List<Emoji>,
    val reactions: List<Reaction>
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Announcement) return false

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    @JsonClass(generateAdapter = true)
    data class Reaction(
        val name: String,
        val count: Int,
        val me: Boolean,
        val url: String? = null,
        @Json(name = "static_url") val staticUrl: String? = null
    )
}
