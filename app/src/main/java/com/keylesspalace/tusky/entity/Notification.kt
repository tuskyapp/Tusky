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

import com.keylesspalace.tusky.entity.Notification.Type
import com.keylesspalace.tusky.entity.Notification.Type.Favourite
import com.keylesspalace.tusky.entity.Notification.Type.Follow
import com.keylesspalace.tusky.entity.Notification.Type.FollowRequest
import com.keylesspalace.tusky.entity.Notification.Type.Mention
import com.keylesspalace.tusky.entity.Notification.Type.ModerationWarning
import com.keylesspalace.tusky.entity.Notification.Type.Reblog
import com.keylesspalace.tusky.entity.Notification.Type.SeveredRelationship
import com.keylesspalace.tusky.entity.Notification.Type.SignUp
import com.keylesspalace.tusky.entity.Notification.Type.Unknown
import com.keylesspalace.tusky.entity.Notification.Type.Update
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
    sealed class Type(val name: String) {
        data class Unknown(val unknownName: String) : Type(unknownName)

        /** Someone mentioned you */
        object Mention : Type("mention")

        /** Someone boosted one of your statuses */
        object Reblog : Type("reblog")

        /** Someone favourited one of your statuses */
        object Favourite : Type("favourite")

        /** Someone followed you */
        object Follow : Type("follow")

        /** Someone requested to follow you */
        object FollowRequest : Type("follow_request")

        /** A poll you have voted in or created has ended */
        object Poll : Type("poll")

        /** Someone you enabled notifications for has posted a status */
        object Status : Type("status")

        /** Someone signed up (optionally sent to admins) */
        object SignUp : Type("admin.sign_up")

        /** A status you interacted with has been updated */
        object Update : Type("update")

        /** A new report has been filed */
        object Report : Type("admin.report")

        /**  Some of your follow relationships have been severed as a result of a moderation or block event **/
        object SeveredRelationship : Type("severed_relationships")

        /** moderation_warning = A moderator has taken action against your account or has sent you a warning **/
        object ModerationWarning : Type("moderation_warning")

        // can't use data objects or this wouldn't work
        override fun toString() = name
    }

    // for Pleroma compatibility that uses Mention type
    fun rewriteToStatusTypeIfNeeded(accountId: String): Notification {
        if (type == Mention && status != null) {
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

/** Notification types for UI display (omits UNKNOWN) */
/** this is not in a companion object so it gets initialized earlier,
 * otherwise it might get initialized when a subclass is loaded,
 * which leds to crash since those subclasses are referenced here */
val visibleNotificationTypes = listOf(Mention, Reblog, Favourite, Follow, FollowRequest, Type.Poll, Type.Status, SignUp, Update, Type.Report, SeveredRelationship, ModerationWarning)

fun notificationTypeFromString(s: String): Type {
    return visibleNotificationTypes.firstOrNull { it.name == s.lowercase() } ?: Unknown(s)
}
