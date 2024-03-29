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

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Entity
@TypeConverters(Converters::class)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Long,
    val inReplyToId: String?,
    val content: String?,
    val contentWarning: String?,
    val sensitive: Boolean,
    val visibility: Status.Visibility,
    val attachments: List<DraftAttachment>,
    val poll: NewPoll?,
    val failedToSend: Boolean,
    val failedToSendNew: Boolean,
    val scheduledAt: String?,
    val language: String?,
    val statusId: String?
)

@JsonClass(generateAdapter = true)
@Parcelize
data class DraftAttachment(
    val uriString: String,
    val description: String?,
    val focus: Attachment.Focus?,
    val type: Type
) : Parcelable {
    val uri: Uri
        get() = uriString.toUri()

    @JsonClass(generateAdapter = false)
    enum class Type {
        IMAGE,
        VIDEO,
        AUDIO
    }
}
