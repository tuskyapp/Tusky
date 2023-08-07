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

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import com.google.gson.annotations.SerializedName
import com.keylesspalace.tusky.core.text.parseAsMastodonHtml
import java.util.Date

data class Status(
    val id: String,
    val url: String?, // not present if it's reblog
    val account: TimelineAccount,
    @SerializedName("in_reply_to_id") val inReplyToId: String?,
    @SerializedName("in_reply_to_account_id") val inReplyToAccountId: String?,
    val reblog: Status?,
    val content: String,
    @SerializedName("created_at") val createdAt: Date,
    @SerializedName("edited_at") val editedAt: Date?,
    val emojis: List<Emoji>,
    @SerializedName("reblogs_count") val reblogsCount: Int,
    @SerializedName("favourites_count") val favouritesCount: Int,
    @SerializedName("replies_count") val repliesCount: Int,
    val reblogged: Boolean,
    val favourited: Boolean,
    val bookmarked: Boolean,
    val sensitive: Boolean,
    @SerializedName("spoiler_text") val spoilerText: String,
    val visibility: StatusVisibility,
    @SerializedName("media_attachments") val attachments: List<Attachment>,
    val mentions: List<Mention>,
    val tags: List<HashTag>?,
    val application: Application?,
    val pinned: Boolean?,
    val muted: Boolean?,
    val poll: Poll?,
    val card: Card?,
    val language: String?,
    val filtered: List<FilterResult>?
) {

    val actionableId: String
        get() = reblog?.id ?: id

    val actionableStatus: Status
        get() = reblog ?: this

    /** Helpers for Java */
    fun copyWithFavourited(favourited: Boolean): Status = copy(favourited = favourited)
    fun copyWithReblogged(reblogged: Boolean): Status = copy(reblogged = reblogged)
    fun copyWithBookmarked(bookmarked: Boolean): Status = copy(bookmarked = bookmarked)
    fun copyWithPoll(poll: Poll?): Status = copy(poll = poll)
    fun copyWithPinned(pinned: Boolean): Status = copy(pinned = pinned)

    fun rebloggingAllowed(): Boolean {
        return (visibility != StatusVisibility.DIRECT && visibility != StatusVisibility.UNKNOWN)
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

    data class Mention(
        val id: String,
        val url: String,
        @SerializedName("acct") val username: String,
        @SerializedName("username") val localUsername: String
    )

    data class Application(
        val name: String,
        val website: String?
    )

    companion object {
        const val MAX_MEDIA_ATTACHMENTS = 4
        const val MAX_POLL_OPTIONS = 4
    }
}
