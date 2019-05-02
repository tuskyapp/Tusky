/* Copyright 2018 Levi Bard
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.entity

import com.google.gson.annotations.SerializedName

data class Filter (
    val id: String,
    val phrase: String,
    val context: List<String>,
    @SerializedName("expires_at") val expiresAt: String?,
    val irreversible: Boolean,
    @SerializedName("whole_word") val wholeWord: Boolean
) {
    companion object {
        const val HOME = "home"
        const val NOTIFICATIONS = "notifications"
        const val PUBLIC = "public"
        const val THREAD = "thread"
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Filter) {
            return false
        }
        val filter = other as Filter?
        return filter?.id.equals(id)
    }
}

