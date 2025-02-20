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
    val report: Report? = null,
    val filtered: Boolean = false,
    val event: RelationshipSeveranceEvent? = null,
    @Json(name = "moderation_warning") val moderationWarning: AccountWarning? = null
) {

    /** From https://docs.joinmastodon.org/entities/Notification/#type */
    @JsonClass(generateAdapter = false)
    sealed class Type(val name: String, @StringRes val uiString: Int) {
        data class Unknown(val unknownName: String) : Type(unknownName, R.string.notification_unknown_name)

        /** Someone mentioned you */
        object Mention : Type("mention", R.string.notification_mention_name)

        /** Someone boosted one of your statuses */
        object Reblog : Type("reblog", R.string.notification_boost_name)

        /** Someone favourited one of your statuses */
        object Favourite : Type("favourite", R.string.notification_favourite_name)

        /** Someone followed you */
        object Follow : Type("follow", R.string.notification_follow_name)

        /** Someone requested to follow you */
        object FollowRequest : Type("follow_request", R.string.notification_follow_request_name)

        /** A poll you have voted in or created has ended */
        object Poll : Type("poll", R.string.notification_poll_name)

        /** Someone you enabled notifications for has posted a status */
        object Status : Type("status", R.string.notification_subscription_name)

        /** Someone signed up (optionally sent to admins) */
        object SignUp : Type("admin.sign_up", R.string.notification_sign_up_name)

        /** A status you interacted with has been updated */
        object Update : Type("update", R.string.notification_update_name)

        /** A new report has been filed */
        object Report : Type("admin.report", R.string.notification_report_name)

        /**  Some of your follow relationships have been severed as a result of a moderation or block event **/
        object SeveredRelationship : Type("severed_relationships", R.string.notification_severed_relationship_name)

        /** moderation_warning = A moderator has taken action against your account or has sent you a warning **/
        object ModerationWarning : Type("moderation_warning", R.string.notification_severed_relationship_name)

        companion object {
            fun byString(s: String): Type {
                return visibleTypes.firstOrNull { it.name == s.lowercase() } ?: Unknown(s)
            }

            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes = listOf(Mention, Reblog, Favourite, Follow, FollowRequest, Poll, Status, SignUp, Update, Report, SeveredRelationship, ModerationWarning)
        }

        // can't use data objects or this wouldn't work
        override fun toString() = name
    }

    // for Pleroma compatibility that uses Mention type
    fun rewriteToStatusTypeIfNeeded(accountId: String): Notification {
        if (type == Type.Mention && status != null) {
            return if (status.mentions.any {
                    it.id == accountId
                }
            ) {
                this
            } else {
                copy(type = Type.Status)
            }
        }
        return this
    }
}
