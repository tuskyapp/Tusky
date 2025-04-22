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

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.components.conversation.ConversationAccountEntity
import com.keylesspalace.tusky.components.systemnotifications.NotificationChannelData
import com.keylesspalace.tusky.createTabDataFromId
import com.keylesspalace.tusky.db.entity.DraftAttachment
import com.keylesspalace.tusky.entity.AccountWarning
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.FilterResult
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PreviewCard
import com.keylesspalace.tusky.entity.RelationshipSeveranceEvent
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.notificationTypeFromString
import com.keylesspalace.tusky.settings.DefaultReplyVisibility
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

@OptIn(ExperimentalStdlibApi::class)
@ProvidedTypeConverter
@Singleton
class Converters @Inject constructor(
    private val moshi: Moshi
) {

    @TypeConverter
    fun jsonToEmojiList(emojiListJson: String?): List<Emoji> = emojiListJson?.let { moshi.adapter<List<Emoji>?>().fromJson(it) }.orEmpty()

    @TypeConverter
    fun emojiListToJson(emojiList: List<Emoji>): String = moshi.adapter<List<Emoji>>().toJson(emojiList)

    @TypeConverter
    fun visibilityToInt(visibility: Status.Visibility?): Int = visibility?.int ?: Status.Visibility.UNKNOWN.int

    @TypeConverter
    fun intToVisibility(visibility: Int): Status.Visibility = Status.Visibility.fromInt(visibility)

    @TypeConverter
    fun defaultReplyVisibilityToInt(visibility: DefaultReplyVisibility?): Int = visibility?.int ?: DefaultReplyVisibility.MATCH_DEFAULT_POST_VISIBILITY.int

    @TypeConverter
    fun intToDefaultReplyVisibility(visibility: Int): DefaultReplyVisibility = DefaultReplyVisibility.fromInt(visibility)

    @TypeConverter
    fun stringToTabData(str: String?): List<TabData>? = str?.split(";")
        ?.map {
            val data = it.split(":")
            createTabDataFromId(
                data[0],
                data.drop(1).map { s -> URLDecoder.decode(s, "UTF-8") }
            )
        }

    @TypeConverter
    fun tabDataToString(tabData: List<TabData>?): String? {
        // List name may include ":"
        return tabData?.joinToString(";") {
            it.id + ":" + it.arguments.joinToString(":") { s -> URLEncoder.encode(s, "UTF-8") }
        }
    }

    @TypeConverter
    fun accountToJson(account: ConversationAccountEntity?): String = moshi.adapter<ConversationAccountEntity?>().toJson(account)

    @TypeConverter
    fun jsonToAccount(accountJson: String?): ConversationAccountEntity? = accountJson?.let { moshi.adapter<ConversationAccountEntity?>().fromJson(it) }

    @TypeConverter
    fun accountListToJson(accountList: List<ConversationAccountEntity>): String = moshi.adapter<List<ConversationAccountEntity>>().toJson(accountList)

    @TypeConverter
    fun jsonToAccountList(accountListJson: String?): List<ConversationAccountEntity> = accountListJson?.let { moshi.adapter<List<ConversationAccountEntity>?>().fromJson(it) }.orEmpty()

    @TypeConverter
    fun attachmentListToJson(attachmentList: List<Attachment>): String = moshi.adapter<List<Attachment>>().toJson(attachmentList)

    @TypeConverter
    fun jsonToAttachmentList(attachmentListJson: String?): List<Attachment> = attachmentListJson?.let { moshi.adapter<List<Attachment>?>().fromJson(it) }.orEmpty()

    @TypeConverter
    fun mentionListToJson(mentionArray: List<Status.Mention>): String = moshi.adapter<List<Status.Mention>>().toJson(mentionArray)

    @TypeConverter
    fun jsonToMentionArray(mentionListJson: String?): List<Status.Mention> = mentionListJson?.let { moshi.adapter<List<Status.Mention>?>().fromJson(it) }.orEmpty()

    @TypeConverter
    fun tagListToJson(tagArray: List<HashTag>?): String = moshi.adapter<List<HashTag>?>().toJson(tagArray)

    @TypeConverter
    fun jsonToTagArray(tagListJson: String?): List<HashTag>? = tagListJson?.let { moshi.adapter<List<HashTag>?>().fromJson(it) }

    @TypeConverter
    fun dateToLong(date: Date?): Long? = date?.time

    @TypeConverter
    fun longToDate(date: Long?): Date? = date?.let { Date(it) }

    @TypeConverter
    fun pollToJson(poll: Poll?): String = moshi.adapter<Poll?>().toJson(poll)

    @TypeConverter
    fun jsonToPoll(pollJson: String?): Poll? = pollJson?.let { moshi.adapter<Poll?>().fromJson(it) }

    @TypeConverter
    fun newPollToJson(newPoll: NewPoll?): String = moshi.adapter<NewPoll?>().toJson(newPoll)

    @TypeConverter
    fun jsonToNewPoll(newPollJson: String?): NewPoll? = newPollJson?.let { moshi.adapter<NewPoll?>().fromJson(it) }

    @TypeConverter
    fun draftAttachmentListToJson(draftAttachments: List<DraftAttachment>): String = moshi.adapter<List<DraftAttachment>>().toJson(draftAttachments)

    @TypeConverter
    fun jsonToDraftAttachmentList(draftAttachmentListJson: String?): List<DraftAttachment> = draftAttachmentListJson?.let { moshi.adapter<List<DraftAttachment>?>().fromJson(it) }.orEmpty()

    @TypeConverter
    fun filterResultListToJson(filterResults: List<FilterResult>?): String = moshi.adapter<List<FilterResult>?>().toJson(filterResults)

    @TypeConverter
    fun jsonToFilterResultList(filterResultListJson: String?): List<FilterResult>? = filterResultListJson?.let { moshi.adapter<List<FilterResult>?>().fromJson(it) }

    @TypeConverter
    fun cardToJson(card: PreviewCard?): String = moshi.adapter<PreviewCard?>().toJson(card)

    @TypeConverter
    fun jsonToCard(cardJson: String?): PreviewCard? = cardJson?.let { moshi.adapter<PreviewCard?>().fromJson(cardJson) }

    @TypeConverter
    fun stringListToJson(list: List<String>?): String? = moshi.adapter<List<String>?>().toJson(list)

    @TypeConverter
    fun jsonToStringList(listJson: String?): List<String>? = listJson?.let { moshi.adapter<List<String>?>().fromJson(it) }

    @TypeConverter
    fun applicationToJson(application: Status.Application?): String = moshi.adapter<Status.Application?>().toJson(application)

    @TypeConverter
    fun jsonToApplication(applicationJson: String?): Status.Application? = applicationJson?.let { moshi.adapter<Status.Application?>().fromJson(it) }

    @TypeConverter
    fun notificationChannelDataListToJson(data: Set<NotificationChannelData>?): String {
        val array = JSONArray()
        data?.forEach {
            array.put(it.name)
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToNotificationChannelDataList(data: String?): Set<NotificationChannelData> {
        val ret = HashSet<NotificationChannelData>()
        data?.let {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val item = array.getString(i)
                try {
                    val type = NotificationChannelData.valueOf(item)
                    ret.add(type)
                } catch (_: IllegalArgumentException) {
                    // ignore, this can happen because we stored individual notification types and not channels before
                }
            }
        }
        return ret
    }

    @TypeConverter
    fun relationshipSeveranceEventToJson(event: RelationshipSeveranceEvent?): String = moshi.adapter<RelationshipSeveranceEvent?>().toJson(event)

    @TypeConverter
    fun jsonToRelationshipSeveranceEvent(eventJson: String?): RelationshipSeveranceEvent? = eventJson?.let { moshi.adapter<RelationshipSeveranceEvent?>().fromJson(it) }

    @TypeConverter
    fun accountWarningToJson(accountWarning: AccountWarning?): String = moshi.adapter<AccountWarning?>().toJson(accountWarning)

    @TypeConverter
    fun jsonToAccountWarning(accountWarningJson: String?): AccountWarning? = accountWarningJson?.let { moshi.adapter<AccountWarning?>().fromJson(it) }

    @TypeConverter
    fun accountWarningToJson(notificationType: Notification.Type): String = notificationType.name

    @TypeConverter
    fun jsonToNotificationType(notificationTypeJson: String): Notification.Type = notificationTypeFromString(notificationTypeJson)
}
