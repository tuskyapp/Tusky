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
import java.util.Date

data class DeletedStatus(
    val text: String?,
    @SerializedName("in_reply_to_id") val inReplyToId: String?,
    @SerializedName("spoiler_text") val spoilerText: String,
    val visibility: StatusVisibility,
    val sensitive: Boolean,
    @SerializedName("media_attachments") val attachments: ArrayList<Attachment>?,
    val poll: Poll?,
    @SerializedName("created_at") val createdAt: Date,
    val language: String?,
) {
    fun isEmpty(): Boolean {
        return text == null && attachments == null
    }
}