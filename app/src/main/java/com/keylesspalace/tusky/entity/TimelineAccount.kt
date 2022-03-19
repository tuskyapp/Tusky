/* Copyright 2017 Andrew Dawson
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

/**
 * Same as [Account], but only with the attributes required in timelines.
 * Prefer this class over [Account] because it uses way less memory & deserializes faster from json.
 */
data class TimelineAccount(
    val id: String,
    @SerializedName("username") val localUsername: String,
    @SerializedName("acct") val username: String,
    @SerializedName("display_name") val displayName: String?, // should never be null per Api definition, but some servers break the contract
    val url: String,
    val avatar: String,
    val bot: Boolean = false,
    val emojis: List<Emoji>? = emptyList(), // nullable for backward compatibility
) {

    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else displayName
}
