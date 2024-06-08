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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class FilterV1(
    val id: String,
    val phrase: String,
    val context: List<String>,
    @Json(name = "expires_at") val expiresAt: Date? = null,
    val irreversible: Boolean,
    @Json(name = "whole_word") val wholeWord: Boolean
) {
    companion object {
        const val HOME = "home"
        const val NOTIFICATIONS = "notifications"
        const val PUBLIC = "public"
        const val THREAD = "thread"
        const val ACCOUNT = "account"
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FilterV1) {
            return false
        }
        return other.id == id
    }

    fun toFilter(): Filter {
        return Filter(
            id = id,
            title = phrase,
            context = context,
            expiresAt = expiresAt,
            filterAction = Filter.Action.WARN.action,
            keywords = listOf(
                FilterKeyword(
                    id = id,
                    keyword = phrase,
                    wholeWord = wholeWord
                )
            )
        )
    }
}
