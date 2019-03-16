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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.JsonAdapter

data class Notification(
        val type: Type,
        val id: String,
        val account: Account,
        val status: Status?) {

    @JsonAdapter(NotificationTypeAdapter::class)
    enum class Type {
        UNKNOWN,
        MENTION,
        REBLOG,
        FAVOURITE,
        FOLLOW;

        companion object {

            @JvmStatic
            fun byString(s: String): Type {
                return when (s) {
                    "mention" -> MENTION
                    "reblog" -> REBLOG
                    "favourite" -> FAVOURITE
                    "follow" -> FOLLOW
                    else -> UNKNOWN
                }
            }

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
        override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): Notification.Type {
            return Notification.Type.byString(json.asString)
        }

    }
}
