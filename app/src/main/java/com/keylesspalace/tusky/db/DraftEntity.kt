/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky.db

import androidx.room.*
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status

@Entity
@TypeConverters(Converters::class)
data class DraftEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val accountId: Long,
        val inReplyToId: String?,
        val content: String?,
        val contentWarning: String?,
        val sensitive: Boolean,
        val visibility: Status.Visibility?,
        val attachments: List<DraftAttachment>,
        val poll: NewPoll?,
        val failedToSend: Boolean
)

data class DraftAttachment(
        val path: String,
        val description: String?,
        val type: ComposeActivity.QueuedMedia.Type
)
