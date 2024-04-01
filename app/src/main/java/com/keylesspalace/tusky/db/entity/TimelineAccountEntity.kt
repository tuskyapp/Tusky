/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.db.entity

import androidx.room.Entity

@Entity(
    primaryKeys = ["serverId", "tuskyAccountId"]
)
data class TimelineAccountEntity(
    val serverId: String,
    val tuskyAccountId: Long,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val url: String,
    val avatar: String,
    val emojis: String,
    val bot: Boolean
)
