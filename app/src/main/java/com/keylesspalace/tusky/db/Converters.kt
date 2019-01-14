/* Copyright 2018 Conny Duck
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

import android.text.Spanned
import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.createTabDataFromId
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.json.SpannedTypeAdapter

class Converters {

    private val gson = GsonBuilder()
            .registerTypeAdapter(Spanned::class.java, SpannedTypeAdapter())
            .create()

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

    @TypeConverter
    fun stringToTabData(str: String?): List<TabData>? {
        return str?.split(";")
                ?.map { createTabDataFromId(it) }
    }

    @TypeConverter
    fun tabDataToString(tabData: List<TabData>?): String? {
        return tabData?.joinToString(";") { it.id }
    }

    @TypeConverter
    fun statusToJson(status: Status?): String {
        return gson.toJson(status)
    }

    @TypeConverter
    fun jsonToStatus(statusJson: String?): Status? {
        Log.d("JSOND", statusJson)
        return gson.fromJson(statusJson, object : TypeToken<Status>() {}.type)
    }

    @TypeConverter
    fun accountListToJson(accountList: List<Account>?): String {
        return gson.toJson(accountList)
    }

    @TypeConverter
    fun jsonToAccountList(accountListJson: String?): List<Account>? {
        return gson.fromJson(accountListJson, object : TypeToken<List<Account>>() {}.type)
    }
}