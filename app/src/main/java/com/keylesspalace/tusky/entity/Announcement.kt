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

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Announcement(
    val id: String,
    val content: String,
    @SerializedName("starts_at") val startsAt: Date?,
    @SerializedName("ends_at") val endsAt: Date?,
    @SerializedName("all_day") val allDay: Boolean,
    @SerializedName("published_at") val publishedAt: Date,
    @SerializedName("updated_at") val updatedAt: Date,
    val read: Boolean,
    val mentions: List<Status.Mention>,
    val statuses: List<Status>,
    val tags: List<HashTag>,
    val emojis: List<Emoji>,
    val reactions: List<Reaction>
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val announcement = other as Announcement?
        return id == announcement?.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    data class Reaction(
        val name: String,
        val count: Int,
        val me: Boolean,
        val url: String?,
        @SerializedName("static_url") val staticUrl: String?
    )
}
