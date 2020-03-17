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

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter

data class Notification(
        val type: Type,
        val id: String,
        val account: Account,
        val status: Status?) {

    @JsonAdapter(NotificationTypeAdapter::class)
    enum class Type(val presentation: String) {
        UNKNOWN("unknown"),
        MENTION("mention"),
        REBLOG("reblog"),
        FAVOURITE("favourite"),
        FOLLOW("follow"),
        FOLLOW_REQUEST("follow_request"),
        POLL("poll");

        companion object {

            @JvmStatic
            fun byString(s: String): Type {
                values().forEach {
                    if (s == it.presentation)
                        return it
                }
                return UNKNOWN
            }
            val asList = listOf(MENTION, REBLOG, FAVOURITE, FOLLOW, FOLLOW_REQUEST, POLL)
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
        val notification = other as Notification?
        return notification?.id == this.id
    }

    class NotificationTypeAdapter : JsonDeserializer<Type> {

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): Type {
            return Type.byString(json.asString)
        }

    }
}
