/* Copyright 2018 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tech.bigfig.roma.entity.Emoji
import tech.bigfig.roma.entity.Status

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun jsonToEmojiList(emojiListJson: String?): List<Emoji>? {
        return gson.fromJson(emojiListJson, object : TypeToken<List<Emoji>>() {}.type)
    }

    @TypeConverter
    fun emojiListToJson(emojiList: List<Emoji>?): String {
        return gson.toJson(emojiList)
    }

    @TypeConverter
    fun visibilityToInt(visibility: Status.Visibility): Int {
        return visibility.num
    }

    @TypeConverter
    fun intToVisibility(visibility: Int): Status.Visibility {
        return Status.Visibility.byNum(visibility)
    }
}