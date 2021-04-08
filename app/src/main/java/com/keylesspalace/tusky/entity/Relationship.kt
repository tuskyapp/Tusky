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

data class Relationship (
    val id: String,
    val following: Boolean,
    @SerializedName("followed_by") val followedBy: Boolean,
    val blocking: Boolean,
    val muting: Boolean,
    @SerializedName("muting_notifications") val mutingNotifications: Boolean,
    val requested: Boolean,
    @SerializedName("showing_reblogs") val showingReblogs: Boolean,
    val subscribing: Boolean? = null, // Pleroma extension
    @SerializedName("domain_blocking") val blockingDomain: Boolean,
    val note: String?, // nullable for backward compatibility / feature detection
    val notifying: Boolean? // since 3.3.0rc
)
