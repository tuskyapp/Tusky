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
import android.text.style.URLSpan
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Status(
    val id: String,
    // not present if it's reblog
    val url: String? = null,
    val account: TimelineAccount,
    @Json(name = "in_reply_to_id") val inReplyToId: String? = null,
    @Json(name = "in_reply_to_account_id") val inReplyToAccountId: String? = null,
    val reblog: Status? = null,
    val content: String,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "edited_at") val editedAt: Date? = null,
    val emojis: List<Emoji>,
    @Json(name = "reblogs_count") val reblogsCount: Int,
    @Json(name = "favourites_count") val favouritesCount: Int,
    @Json(name = "replies_count") val repliesCount: Int,
    val reblogged: Boolean = false,
    val favourited: Boolean = false,
    val bookmarked: Boolean = false,
    val sensitive: Boolean,
    @Json(name = "spoiler_text") val spoilerText: String,
    val visibility: Visibility,
    @Json(name = "media_attachments") val attachments: List<Attachment>,
    val mentions: List<Mention>,
    // Use null to mark the absence of tags because of semantic differences in LinkHelper
    val tags: List<HashTag> = emptyList(),
    val application: Application? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val poll: Poll? = null,
    /** Preview card for links included within status content. */
    val card: PreviewCard? = null,
    /** ISO 639 language code for this status. */
    val language: String? = null,
    /** If the current token has an authorized user: The filter and keywords that matched this status.
     *  Iceshrimp and maybe other implementations explicitly send filtered=null so we can't default to empty list. */
    val filtered: List<FilterResult>? = null
) {

    val actionableId: String
        get() = reblog?.id ?: id

    val actionableStatus: Status
        get() = reblog ?: this

    @JsonClass(generateAdapter = false)
    enum class Visibility(val int: Int) {
        UNKNOWN(0),

        @Json(name = "public")
        PUBLIC(1),

        @Json(name = "unlisted")
        UNLISTED(2),

        @Json(name = "private")
        PRIVATE(3),

        @Json(name = "direct")
        DIRECT(4);

        val stringValue: String
            get() = when (this) {
                PUBLIC -> "public"
                UNLISTED -> "unlisted"
                PRIVATE -> "private"
                DIRECT -> "direct"
                UNKNOWN -> "unknown"
            }

        companion object {
            fun fromInt(int: Int): Visibility {
                return when (int) {
                    4 -> DIRECT
                    3 -> PRIVATE
                    2 -> UNLISTED
                    1 -> PUBLIC
                    0 -> UNKNOWN
                    else -> UNKNOWN
                }
            }

            fun fromStringValue(s: String): Visibility {
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

    val isRebloggingAllowed: Boolean
        get() {
            return (visibility != Visibility.DIRECT && visibility != Visibility.UNKNOWN)
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
            createdAt = createdAt,
            language = language
        )
    }

    private fun getEditableText(): String {
        val contentSpanned = content.parseAsMastodonHtml()
        val builder = SpannableStringBuilder(content.parseAsMastodonHtml())
        for (span in contentSpanned.getSpans(0, content.length, URLSpan::class.java)) {
            val url = span.url
            for ((_, url1, username) in mentions) {
                if (url == url1) {
                    val start = builder.getSpanStart(span)
                    val end = builder.getSpanEnd(span)
                    if (start >= 0 && end >= 0) {
                        builder.replace(start, end, "@$username")
                    }
                    break
                }
            }
        }
        return builder.toString()
    }

    @JsonClass(generateAdapter = true)
    data class Mention(
        val id: String,
        val url: String,
        @Json(name = "acct") val username: String,
        @Json(name = "username") val localUsername: String
    )

    @JsonClass(generateAdapter = true)
    data class Application(
        val name: String,
        val website: String? = null
    )

    companion object {
        const val MAX_MEDIA_ATTACHMENTS = 4
        const val MAX_POLL_OPTIONS = 4
    }
}
