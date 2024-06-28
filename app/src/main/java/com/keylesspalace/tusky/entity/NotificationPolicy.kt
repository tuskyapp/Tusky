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
    @Json(name = "filter_not_following") val filterNotFollowing: Boolean,
    @Json(name = "filter_not_followers") val filterNotFollowers: Boolean,
    @Json(name = "filter_new_accounts") val filterNewAccounts: Boolean,
    @Json(name = "filter_private_mentions") val filterPrivateMentions: Boolean,
    val summary: NotificationPolicySummary
)

@JsonClass(generateAdapter = true)
data class NotificationPolicySummary(
    @Json(name = "pending_requests_count") val pendingRequestsCount: Int,
    @Json(name = "pending_notifications_count") val pendingNotificationsCount: Int
)
