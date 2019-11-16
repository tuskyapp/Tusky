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

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import com.google.gson.annotations.SerializedName
import java.util.*

data class Status(
        var id: String,
        var url: String?, // not present if it's reblog
        val account: Account,
        @SerializedName("in_reply_to_id") var inReplyToId: String?,
        @SerializedName("in_reply_to_account_id") val inReplyToAccountId: String?,
        val reblog: Status?,
        val content: Spanned,
        @SerializedName("created_at") val createdAt: Date,
        val emojis: List<Emoji>,
        @SerializedName("reblogs_count") val reblogsCount: Int,
        @SerializedName("favourites_count") val favouritesCount: Int,
        var reblogged: Boolean,
        var favourited: Boolean,
        var bookmarked: Boolean,
        var sensitive: Boolean,
        @SerializedName("spoiler_text") val spoilerText: String,
        val visibility: Visibility,
        @SerializedName("media_attachments") var attachments: ArrayList<Attachment>,
        val mentions: Array<Mention>,
        val application: Application?,
        var pinned: Boolean?,
        val poll: Poll?,
        val card: Card?
) {

    val actionableId: String
        get() = reblog?.id ?: id

    val actionableStatus: Status
        get() = reblog ?: this


    enum class Visibility(val num: Int) {
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
            fun byNum(num: Int): Visibility {
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
            fun byString(s: String): Visibility {
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

    fun rebloggingAllowed(): Boolean {
        return (visibility != Visibility.DIRECT && visibility != Visibility.UNKNOWN)
    }

    fun isPinned(): Boolean {
        return pinned ?: false
    }

    fun toDeletedStatus(): DeletedStatus {
        return DeletedStatus(
                text = getEditableText(),
                inReplyToId = inReplyToId,
                spoilerText = spoilerText,
                visibility = visibility,
                sensitive = sensitive,
                attachments = attachments,
                poll = poll,
                createdAt = createdAt
        )
    }

    private fun getEditableText(): String {
        val builder = SpannableStringBuilder(content)
        for (span in content.getSpans(0, content.length, URLSpan::class.java)) {
            val url = span.url
            for ((_, url1, username) in mentions) {
                if (url == url1) {
                    val start = builder.getSpanStart(span)
                    val end = builder.getSpanEnd(span)
                    builder.replace(start, end, "@$username")
                    break
                }
            }
        }
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val status = other as Status?
        return id == status?.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


    data class Mention (
        val id: String,
        val url: String,
        @SerializedName("acct") val username: String,
        @SerializedName("username") val localUsername: String
    )

    data class Application (
        val name: String,
        val website: String?
    )

    companion object {
        const val MAX_MEDIA_ATTACHMENTS = 4
        const val MAX_POLL_OPTIONS = 4
    }
}
