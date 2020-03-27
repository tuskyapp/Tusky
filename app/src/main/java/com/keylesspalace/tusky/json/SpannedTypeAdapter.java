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

package com.keylesspalace.tusky.json;

import android.text.Spanned;
import android.text.SpannedString;

import androidx.core.text.HtmlCompat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class SpannedTypeAdapter implements JsonDeserializer<Spanned>, JsonSerializer<Spanned> {
    @Override
    public Spanned deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        String string = json.getAsString();
        if (string != null) {
            /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
             * all status contents do, so it should be trimmed. */
            return (Spanned)trimTrailingWhitespace(HtmlCompat.fromHtml(string, HtmlCompat.FROM_HTML_MODE_LEGACY));
        } else {
            return new SpannedString("");
        }
    }

    @Override
    public JsonElement serialize(Spanned src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(HtmlCompat.toHtml(src, HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL));
    }

    private static CharSequence trimTrailingWhitespace(CharSequence s) {
        int i = s.length();
        do {
            i--;
        } while (i >= 0 && Character.isWhitespace(s.charAt(i)));
        return s.subSequence(0, i + 1);
    }
}
