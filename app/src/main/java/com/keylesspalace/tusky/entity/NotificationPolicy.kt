/* Copyright 2024 Tusky contributors
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
data class NotificationPolicy(
    @Json(name = "for_not_following") val forNotFollowing: State,
    @Json(name = "for_not_followers") val forNotFollowers: State,
    @Json(name = "for_new_accounts") val forNewAccounts: State,
    @Json(name = "for_private_mentions") val forPrivateMentions: State,
    @Json(name = "for_limited_accounts") val forLimitedAccounts: State,
    val summary: Summary
) {
    @JsonClass(generateAdapter = false)
    enum class State {
        @Json(name = "accept")
        ACCEPT,

        @Json(name = "filter")
        FILTER,

        @Json(name = "drop")
        DROP
    }

    @JsonClass(generateAdapter = true)
    data class Summary(
        @Json(name = "pending_requests_count") val pendingRequestsCount: Int,
        @Json(name = "pending_notifications_count") val pendingNotificationsCount: Int
    )
}
