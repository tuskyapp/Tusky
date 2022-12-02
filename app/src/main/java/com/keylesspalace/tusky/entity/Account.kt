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
import java.util.Date

data class Account(
    val id: String,
    @SerializedName("username") val localUsername: String,
    @SerializedName("acct") val username: String,
    @SerializedName("display_name") val displayName: String?, // should never be null per Api definition, but some servers break the contract
    @SerializedName("created_at") val createdAt: Date,
    val note: String,
    val url: String,
    val avatar: String,
    val header: String,
    val locked: Boolean = false,
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("statuses_count") val statusesCount: Int = 0,
    val source: AccountSource? = null,
    val bot: Boolean = false,
    val emojis: List<Emoji>? = emptyList(), // nullable for backward compatibility
    val fields: List<Field>? = emptyList(), // nullable for backward compatibility
    val moved: Account? = null

) {

    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else displayName

    fun isRemote(): Boolean = this.username != this.localUsername
}

data class AccountSource(
    val privacy: Status.Visibility?,
    val sensitive: Boolean?,
    val note: String?,
    val fields: List<StringField>?,
    val language: String?,
)

data class Field(
    val name: String,
    val value: String,
    @SerializedName("verified_at") val verifiedAt: Date?
)

data class StringField(
    val name: String,
    val value: String
)
