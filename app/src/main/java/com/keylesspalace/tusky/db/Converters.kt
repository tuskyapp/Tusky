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
import androidx.core.text.parseAsHtml
import androidx.core.text.toHtml
import androidx.room.TypeConverter
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.components.conversation.ConversationAccountEntity
import com.keylesspalace.tusky.createTabDataFromId
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.json.SpannedTypeAdapter
import com.keylesspalace.tusky.util.trimTrailingWhitespace
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

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
    fun visibilityToInt(visibility: Status.Visibility?): Int {
        return visibility?.num ?: Status.Visibility.UNKNOWN.num
    }

    @TypeConverter
    fun intToVisibility(visibility: Int): Status.Visibility {
        return Status.Visibility.byNum(visibility)
    }

    @TypeConverter
    fun stringToTabData(str: String?): List<TabData>? {
        return str?.split(";")
                ?.map {
                    val data = it.split(":")
                    createTabDataFromId(data[0], data.drop(1).map { s -> URLDecoder.decode(s, "UTF-8") })
                }
    }

    @TypeConverter
    fun tabDataToString(tabData: List<TabData>?): String? {
        // List name may include ":"
        return tabData?.joinToString(";") { it.id + ":" + it.arguments.joinToString(":") { s -> URLEncoder.encode(s, "UTF-8") } }
    }

    @TypeConverter
    fun accountToJson(account: ConversationAccountEntity?): String {
        return gson.toJson(account)
    }

    @TypeConverter
    fun jsonToAccount(accountJson: String?): ConversationAccountEntity? {
        return gson.fromJson(accountJson, ConversationAccountEntity::class.java)
    }

    @TypeConverter
    fun accountListToJson(accountList: List<ConversationAccountEntity>?): String {
        return gson.toJson(accountList)
    }

    @TypeConverter
    fun jsonToAccountList(accountListJson: String?): List<ConversationAccountEntity>? {
        return gson.fromJson(accountListJson, object : TypeToken<List<ConversationAccountEntity>>() {}.type)
    }

    @TypeConverter
    fun attachmentListToJson(attachmentList: List<Attachment>?): String {
        return gson.toJson(attachmentList)
    }

    @TypeConverter
    fun jsonToAttachmentList(attachmentListJson: String?): ArrayList<Attachment>? {
        return gson.fromJson(attachmentListJson, object : TypeToken<ArrayList<Attachment>>() {}.type)
    }

    @TypeConverter
    fun mentionArrayToJson(mentionArray: Array<Status.Mention>?): String? {
        return gson.toJson(mentionArray)
    }

    @TypeConverter
    fun jsonToMentionArray(mentionListJson: String?): Array<Status.Mention>? {
        return gson.fromJson(mentionListJson, object : TypeToken<Array<Status.Mention>>() {}.type)
    }

    @TypeConverter
    fun dateToLong(date: Date): Long {
        return date.time
    }

    @TypeConverter
    fun longToDate(date: Long): Date {
        return Date(date)
    }

    @TypeConverter
    fun spannedToString(spanned: Spanned?): String? {
        if(spanned == null) {
            return null
        }
        return spanned.toHtml()
    }

    @TypeConverter
    fun stringToSpanned(spannedString: String?): Spanned? {
        if(spannedString == null) {
            return null
        }
        return spannedString.parseAsHtml().trimTrailingWhitespace()
    }

    @TypeConverter
    fun pollToJson(poll: Poll?): String? {
        return gson.toJson(poll)
    }

    @TypeConverter
    fun jsonToPoll(pollJson: String?): Poll? {
        return gson.fromJson(pollJson, Poll::class.java)
    }

}