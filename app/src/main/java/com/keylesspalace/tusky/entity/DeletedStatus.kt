/* Copyright 2019 Tusky contributors
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
import java.util.*

data class DeletedStatus(
        var text: String?,
        @SerializedName("in_reply_to_id") var inReplyToId: String?,
        @SerializedName("spoiler_text") val spoilerText: String,
        val visibility: Status.Visibility,
        val sensitive: Boolean,
        @SerializedName("media_attachments") var attachments: ArrayList<Attachment>?,
        val poll: Poll?,
        @SerializedName("created_at") val createdAt: Date
) {
    fun isEmpty(): Boolean {
        return text == null && attachments == null;
    }
}