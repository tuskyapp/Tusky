/*
 * Copyright 2025 Tusky Contributors
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

package com.keylesspalace.tusky.json

import com.keylesspalace.tusky.entity.Notification
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

class NotificationTypeAdapter : JsonAdapter<Notification.Type>() {

    override fun fromJson(reader: JsonReader): Notification.Type {
        return Notification.Type.byString(reader.nextString())
    }

    override fun toJson(writer: JsonWriter, value: Notification.Type?) {
        writer.value(value?.name)
    }
}
