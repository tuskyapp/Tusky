/* Copyright 2025 Tusky Contributors
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

@JsonClass(generateAdapter = true)
data class RelationshipSeveranceEvent(
    val id: String,
    val type: Type,
    @Json(name = "target_name") val targetName: String,
    @Json(name = "followers_count") val followersCount: Int,
    @Json(name = "following_count") val followingCount: Int
) {

    @JsonClass(generateAdapter = false)
    enum class Type {
        @Json(name = "domain_block")
        DOMAIN_BLOCK,

        @Json(name = "user_domain_block")
        USER_DOMAIN_BLOCK,

        @Json(name = "account_suspension")
        ACCOUNT_SUSPENSION,
    }
}
