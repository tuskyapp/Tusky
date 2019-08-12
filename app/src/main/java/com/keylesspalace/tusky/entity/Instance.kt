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

data class Instance (
        val uri: String,
        val title: String,
        val description: String,
        val email: String,
        val version: String,
        val urls: Map<String,String>,
        val stats: Map<String,Int>?,
        val thumbnail: String?,
        val languages: List<String>,
        @SerializedName("contact_account") val contactAccount: Account,
        @SerializedName("max_toot_chars") val maxTootChars: Int?,
        @SerializedName("max_bio_chars") val maxBioChars: Int?,
        @SerializedName("poll_limits") val pollLimits: PollLimits?
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

data class PollLimits (
        @SerializedName("max_options") val maxOptions: Int?,
        @SerializedName("max_option_chars") val maxOptionChars: Int?
)
