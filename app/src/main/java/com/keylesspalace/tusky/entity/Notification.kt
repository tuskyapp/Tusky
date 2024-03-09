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

import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Notification(
    val type: Type,
    val id: String,
    val account: TimelineAccount,
    val status: Status? = null,
    val report: Report? = null
) {

    /** From https://docs.joinmastodon.org/entities/Notification/#type */
    @JsonClass(generateAdapter = false)
    enum class Type(val presentation: String, @StringRes val uiString: Int) {
        UNKNOWN("unknown", R.string.notification_unknown_name),

        /** Someone mentioned you */
        @Json(name = "mention")
        MENTION("mention", R.string.notification_mention_name),

        /** Someone boosted one of your statuses */
        @Json(name = "reblog")
        REBLOG("reblog", R.string.notification_boost_name),

        /** Someone favourited one of your statuses */
        @Json(name = "favourite")
        FAVOURITE("favourite", R.string.notification_favourite_name),

        /** Someone followed you */
        @Json(name = "follow")
        FOLLOW("follow", R.string.notification_follow_name),

        /** Someone requested to follow you */
        @Json(name = "follow_request")
        FOLLOW_REQUEST("follow_request", R.string.notification_follow_request_name),

        /** A poll you have voted in or created has ended */
        @Json(name = "poll")
        POLL("poll", R.string.notification_poll_name),

        /** Someone you enabled notifications for has posted a status */
        @Json(name = "status")
        STATUS("status", R.string.notification_subscription_name),

        /** Someone signed up (optionally sent to admins) */
        @Json(name = "admin.sign_up")
        SIGN_UP("admin.sign_up", R.string.notification_sign_up_name),

        /** A status you interacted with has been updated */
        @Json(name = "update")
        UPDATE("update", R.string.notification_update_name),

        /** A new report has been filed */
        @Json(name = "admin.report")
        REPORT("admin.report", R.string.notification_report_name);

        companion object {
            @JvmStatic
            fun byString(s: String): Type {
                return entries.firstOrNull { it.presentation == s } ?: UNKNOWN
            }

            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes =
                listOf(MENTION, REBLOG, FAVOURITE, FOLLOW, FOLLOW_REQUEST, POLL, STATUS, SIGN_UP, UPDATE, REPORT)
        }

        override fun toString(): String {
            return presentation
        }
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Notification) {
            return false
        }
        return other.id == this.id
    }

    /** Helper for Java */
    fun copyWithStatus(status: Status?): Notification = copy(status = status)

    // for Pleroma compatibility that uses Mention type
    fun rewriteToStatusTypeIfNeeded(accountId: String): Notification {
        if (type == Type.MENTION && status != null) {
            return if (status.mentions.any {
                    it.id == accountId
                }
            ) {
                this
            } else {
                copy(type = Type.STATUS)
            }
        }
        return this
    }
}
