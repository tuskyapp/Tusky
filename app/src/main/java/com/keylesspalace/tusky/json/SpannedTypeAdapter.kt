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

package com.keylesspalace.tusky.json

import android.text.Spanned
import android.text.SpannedString
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import com.google.gson.*
import com.keylesspalace.tusky.util.trimTrailingWhitespace
import java.lang.reflect.Type

class SpannedTypeAdapter : JsonDeserializer<Spanned>, JsonSerializer<Spanned?> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Spanned {
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * all status contents do, so it should be trimmed. */
        return json.asString?.parseAsHtml()?.trimTrailingWhitespace() ?: SpannedString("")
    }

    override fun serialize(src: Spanned?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(HtmlCompat.toHtml(src!!, HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL))
    }
}