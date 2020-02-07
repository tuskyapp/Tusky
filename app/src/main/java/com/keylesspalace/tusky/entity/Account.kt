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

import android.os.Parcel
import android.os.Parcelable
import android.text.Spanned

import com.google.gson.annotations.SerializedName
import com.keylesspalace.tusky.util.HtmlUtils
import kotlinx.android.parcel.Parceler
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.WriteWith
import java.util.*

@Parcelize
data class Account(
        val id: String,
        @SerializedName("username") val localUsername: String,
        @SerializedName("acct") val username: String,
        @SerializedName("display_name") val displayName: String?, // should never be null per Api definition, but some servers break the contract
        val note: @WriteWith<SpannedParceler>() Spanned,
        val url: String,
        val avatar: String,
        val header: String,
        val locked: Boolean = false,
        @SerializedName("followers_count") val followersCount: Int = 0,
        @SerializedName("following_count") val followingCount: Int = 0,
        @SerializedName("statuses_count") val statusesCount: Int = 0,
        val source: AccountSource? = null,
        val bot: Boolean = false,
        val emojis: List<Emoji>? = emptyList(),  // nullable for backward compatibility
        val fields: List<Field>? = emptyList(),  //nullable for backward compatibility
        val moved: Account? = null

) : Parcelable {

    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else displayName

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Account) {
            return false
        }
        return other.id == this.id
    }

    fun deepEquals(other: Account): Boolean {
        return id == other.id
                && localUsername == other.localUsername
                && displayName == other.displayName
                && note == other.note
                && url == other.url
                && avatar == other.avatar
                && header == other.header
                && locked == other.locked
                && followersCount == other.followersCount
                && followingCount == other.followingCount
                && statusesCount == other.statusesCount
                && source == other.source
                && bot == other.bot
                && emojis == other.emojis
                && fields == other.fields
                && moved == other.moved
    }

    fun isRemote(): Boolean = this.username != this.localUsername
}

@Parcelize
data class AccountSource(
        val privacy: Status.Visibility,
        val sensitive: Boolean,
        val note: String,
        val fields: List<StringField>?
): Parcelable

@Parcelize
data class Field (
        val name: String,
        val value: @WriteWith<SpannedParceler>() Spanned,
        @SerializedName("verified_at") val verifiedAt: Date?
): Parcelable

@Parcelize
data class StringField (
        val name: String,
        val value: String
): Parcelable

object SpannedParceler : Parceler<Spanned> {
    override fun create(parcel: Parcel): Spanned = HtmlUtils.fromHtml(parcel.readString())

    override fun Spanned.write(parcel: Parcel, flags: Int) {
        parcel.writeString(HtmlUtils.toHtml(this))
    }
}