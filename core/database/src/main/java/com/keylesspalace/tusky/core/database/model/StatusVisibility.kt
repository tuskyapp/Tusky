/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.core.database.model

import com.google.gson.annotations.SerializedName

enum class StatusVisibility(val num: Int) {
    UNKNOWN(0),

    @SerializedName("public")
    PUBLIC(1),

    @SerializedName("unlisted")
    UNLISTED(2),

    @SerializedName("private")
    PRIVATE(3),

    @SerializedName("direct")
    DIRECT(4);

    fun serverString(): String {
        return when (this) {
            PUBLIC -> "public"
            UNLISTED -> "unlisted"
            PRIVATE -> "private"
            DIRECT -> "direct"
            UNKNOWN -> "unknown"
        }
    }

    companion object {

        @JvmStatic
        fun byNum(num: Int): StatusVisibility {
            return when (num) {
                4 -> DIRECT
                3 -> PRIVATE
                2 -> UNLISTED
                1 -> PUBLIC
                0 -> UNKNOWN
                else -> UNKNOWN
            }
        }

        @JvmStatic
        fun byString(s: String): StatusVisibility {
            return when (s) {
                "public" -> PUBLIC
                "unlisted" -> UNLISTED
                "private" -> PRIVATE
                "direct" -> DIRECT
                "unknown" -> UNKNOWN
                else -> UNKNOWN
            }
        }
    }
}
