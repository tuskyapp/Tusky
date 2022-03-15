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

import android.text.Spanned
import com.google.gson.annotations.SerializedName
import java.util.Date

data class Account(
    val id: String,
    @SerializedName("username") val localUsername: String,
    @SerializedName("acct") val username: String,
    @SerializedName("display_name") val displayName: String?, // should never be null per Api definition, but some servers break the contract
    val note: Spanned,
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

    /**
     * overriding equals & hashcode because Spanned does not always compare correctly otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Account

        if (id != other.id) return false
        if (localUsername != other.localUsername) return false
        if (username != other.username) return false
        if (displayName != other.displayName) return false
        if (note.toString() != other.note.toString()) return false
        if (url != other.url) return false
        if (avatar != other.avatar) return false
        if (header != other.header) return false
        if (locked != other.locked) return false
        if (followersCount != other.followersCount) return false
        if (followingCount != other.followingCount) return false
        if (statusesCount != other.statusesCount) return false
        if (source != other.source) return false
        if (bot != other.bot) return false
        if (emojis != other.emojis) return false
        if (fields != other.fields) return false
        if (moved != other.moved) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + localUsername.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + note.toString().hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + avatar.hashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + locked.hashCode()
        result = 31 * result + followersCount
        result = 31 * result + followingCount
        result = 31 * result + statusesCount
        result = 31 * result + (source?.hashCode() ?: 0)
        result = 31 * result + bot.hashCode()
        result = 31 * result + (emojis?.hashCode() ?: 0)
        result = 31 * result + (fields?.hashCode() ?: 0)
        result = 31 * result + (moved?.hashCode() ?: 0)
        return result
    }
}

data class AccountSource(
    val privacy: Status.Visibility?,
    val sensitive: Boolean?,
    val note: String?,
    val fields: List<StringField>?
)

data class Field(
    val name: String,
    val value: Spanned,
    @SerializedName("verified_at") val verifiedAt: Date?
)

data class StringField(
    val name: String,
    val value: String
)
