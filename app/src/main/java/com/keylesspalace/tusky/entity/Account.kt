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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Account(
    val id: String,
    @Json(name = "username") val localUsername: String,
    @Json( name = "acct") val username: String,
    // should never be null per Api definition, but some servers break the contract
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "created_at") val createdAt: Date,
    val note: String,
    val url: String,
    val avatar: String,
    val header: String,
    val locked: Boolean = false,
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "following_count") val followingCount: Int = 0,
    @Json(name = "statuses_count") val statusesCount: Int = 0,
    val source: AccountSource? = null,
    val bot: Boolean = false,
    // default value for backward compatibility
    val emojis: List<Emoji> = emptyList(),
    // default value for backward compatibility
    val fields: List<Field> = emptyList(),
    val moved: Account? = null,
    val roles: List<Role> = emptyList()
) {

    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else {
            displayName
        }

    val isRemote : Boolean
        get() = this.username != this.localUsername
}

@JsonClass(generateAdapter = true)
data class AccountSource(
    val privacy: Status.Visibility = Status.Visibility.PUBLIC,
    val sensitive: Boolean? = null,
    val note: String? = null,
    val fields: List<StringField> = emptyList(),
    val language: String? = null
)

@JsonClass(generateAdapter = true)
data class Field(
    val name: String,
    val value: String,
    @Json(name = "verified_at") val verifiedAt: Date? = null
)

@JsonClass(generateAdapter = true)
data class StringField(
    val name: String,
    val value: String
)

@JsonClass(generateAdapter = true)
data class Role(
    val name: String,
    val color: String
)
