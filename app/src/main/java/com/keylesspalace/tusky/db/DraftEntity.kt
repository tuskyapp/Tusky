/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky.db

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.internal.Util
import java.lang.reflect.Type
import kotlinx.parcelize.Parcelize

@Entity
@TypeConverters(Converters::class)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Long,
    val inReplyToId: String?,
    val content: String?,
    val contentWarning: String?,
    val sensitive: Boolean,
    val visibility: Status.Visibility,
    val attachments: List<DraftAttachment>,
    val poll: NewPoll?,
    val failedToSend: Boolean,
    val failedToSendNew: Boolean,
    val scheduledAt: String?,
    val language: String?,
    val statusId: String?
)

@Parcelize
data class DraftAttachment(
    val uriString: String,
    val description: String?,
    val focus: Attachment.Focus?,
    val type: Type
) : Parcelable {
    val uri: Uri
        get() = uriString.toUri()

    @JsonClass(generateAdapter = false)
    enum class Type {
        IMAGE,
        VIDEO,
        AUDIO
    }
}

/**
 * This custom adapter exists to support alternate names.
 * The alternate names are here because we accidentally published versions were DraftAttachment was minified
 * Tusky 15: uriString = e, description = f, type = g
 * Tusky 16 beta: uriString = i, description = j, type = k
 */
class DraftAttachmentJsonAdapter(moshi: Moshi) : JsonAdapter<DraftAttachment>() {
    private val options: JsonReader.Options = JsonReader.Options.of(
        "uriString", "e", "i",
        "description", "f", "j",
        "focus",
        "type", "g", "k"
    )

    private val stringAdapter: JsonAdapter<String> =
        moshi.adapter(String::class.java, emptySet(), "uriString")

    private val nullableStringAdapter: JsonAdapter<String?> =
        moshi.adapter(String::class.java, emptySet(), "description")

    private val nullableFocusAdapter: JsonAdapter<Attachment.Focus?> =
        moshi.adapter(Attachment.Focus::class.java, emptySet(), "focus")

    private val typeAdapter: JsonAdapter<DraftAttachment.Type> =
        moshi.adapter(DraftAttachment.Type::class.java, emptySet(), "type")

    override fun fromJson(reader: JsonReader): DraftAttachment {
        var uriString: String? = null
        var description: String? = null
        var focus: Attachment.Focus? = null
        var type: DraftAttachment.Type? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.selectName(options)) {
                0, 1, 2 -> uriString = stringAdapter.fromJson(reader)
                    ?: throw Util.unexpectedNull("uriString", "uriString", reader)

                3, 4, 5 -> description = nullableStringAdapter.fromJson(reader)
                6 -> focus = nullableFocusAdapter.fromJson(reader)
                7, 8, 9 -> type = typeAdapter.fromJson(reader)
                    ?: throw Util.unexpectedNull("type", "type", reader)

                -1 -> {
                    // Unknown name, skip it.
                    reader.skipName()
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
        return DraftAttachment(
            uriString = uriString ?: throw Util.missingProperty("uriString", "uriString", reader),
            description = description,
            focus = focus,
            type = type ?: throw Util.missingProperty("type", "type", reader)
        )
    }

    override fun toJson(writer: JsonWriter, value: DraftAttachment?) {
        requireNotNull(value)

        writer.beginObject()
        writer.name("uriString")
        stringAdapter.toJson(writer, value.uriString)
        writer.name("description")
        nullableStringAdapter.toJson(writer, value.description)
        writer.name("focus")
        nullableFocusAdapter.toJson(writer, value.focus)
        writer.name("type")
        typeAdapter.toJson(writer, value.type)
        writer.endObject()
    }

    companion object {
        val FACTORY = object : Factory {
            override fun create(
                type: Type,
                annotations: Set<Annotation>,
                moshi: Moshi
            ): JsonAdapter<*>? {
                if (annotations.isNotEmpty() || type != DraftAttachment::class.java) {
                    return null
                }
                return DraftAttachmentJsonAdapter(moshi).nullSafe()
            }
        }
    }
}
