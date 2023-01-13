// https://github.com/google/gson/blob/master/extras/src/main/java/com/google/gson/typeadapters/UtcDateTypeAdapter.java
/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.keylesspalace.tusky.json

import android.util.Log
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.util.Date

class Rfc3339DateJsonAdapter : TypeAdapter<Date?>() {

    @Throws(IOException::class)
    override fun write(writer: JsonWriter, date: Date?) {
        if (date == null) {
            writer.nullValue()
        } else {
            writer.value(date.formatIsoDate())
        }
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Date? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> {
                try {
                    reader.nextString().parseIsoDate()
                } catch (jpe: JsonParseException) {
                    Log.w("Rfc3339DateJsonAdapter", jpe)
                    null
                }
            }
        }
    }
}
