package com.keylesspalace.tusky.components.timeline

import android.text.SpannedString
import androidx.core.text.parseAsHtml
import androidx.core.text.toHtml
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keylesspalace.tusky.db.TimelineAccountEntity
import com.keylesspalace.tusky.db.TimelineStatusEntity
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.trimTrailingWhitespace
import java.util.Date

data class Placeholder(val id: String)

typealias TimelineStatus = Either<Placeholder, Status>

private val attachmentArrayListType = object : TypeToken<ArrayList<Attachment>>() {}.type
private val emojisListType = object : TypeToken<List<Emoji>>() {}.type
private val mentionListType = object : TypeToken<List<Status.Mention>>() {}.type

fun Account.toEntity(accountId: Long, gson: Gson): TimelineAccountEntity {
    return TimelineAccountEntity(
        serverId = id,
        timelineUserId = accountId,
        localUsername = localUsername,
        username = username,
        displayName = name,
        url = url,
        avatar = avatar,
        emojis = gson.toJson(emojis),
        bot = bot
    )
}

fun TimelineAccountEntity.toAccount(gson: Gson): Account {
    return Account(
        id = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        note = SpannedString(""),
        url = url,
        avatar = avatar,
        header = "",
        locked = false,
        followingCount = 0,
        followersCount = 0,
        statusesCount = 0,
        source = null,
        bot = bot,
        emojis = gson.fromJson(emojis, emojisListType),
        fields = null,
        moved = null
    )
}

fun Placeholder.toEntity(timelineUserId: Long): TimelineStatusEntity {
    return TimelineStatusEntity(
        serverId = this.id,
        url = null,
        timelineUserId = timelineUserId,
        authorServerId = null,
        inReplyToId = null,
        inReplyToAccountId = null,
        content = null,
        createdAt = 0L,
        emojis = null,
        reblogsCount = 0,
        favouritesCount = 0,
        reblogged = false,
        favourited = false,
        bookmarked = false,
        sensitive = false,
        spoilerText = null,
        visibility = null,
        attachments = null,
        mentions = null,
        application = null,
        reblogServerId = null,
        reblogAccountId = null,
        poll = null,
        muted = false,
        expanded = false,
        contentCollapsed = false,
        contentHidden = false
    )
}

fun Status.toEntity(
    timelineUserId: Long,
    gson: Gson
): TimelineStatusEntity {
    val actionable = actionableStatus
    return TimelineStatusEntity(
        serverId = this.id,
        url = actionable.url!!,
        timelineUserId = timelineUserId,
        authorServerId = actionable.account.id,
        inReplyToId = actionable.inReplyToId,
        inReplyToAccountId = actionable.inReplyToAccountId,
        content = actionable.content.toHtml(),
        createdAt = actionable.createdAt.time,
        emojis = actionable.emojis.let(gson::toJson),
        reblogsCount = actionable.reblogsCount,
        favouritesCount = actionable.favouritesCount,
        reblogged = actionable.reblogged,
        favourited = actionable.favourited,
        bookmarked = actionable.bookmarked,
        sensitive = actionable.sensitive,
        spoilerText = actionable.spoilerText,
        visibility = actionable.visibility,
        attachments = actionable.attachments.let(gson::toJson),
        mentions = actionable.mentions.let(gson::toJson),
        application = actionable.application.let(gson::toJson),
        reblogServerId = reblog?.id,
        reblogAccountId = reblog?.let { this.account.id },
        poll = actionable.poll.let(gson::toJson),
        muted = actionable.muted,
        expanded = false,
        contentHidden = false,
        contentCollapsed = false
    )
}

fun TimelineStatusWithAccount.toStatus(gson: Gson): TimelineStatus {
    if (this.status.authorServerId == null) {
        return Either.Left(Placeholder(this.status.serverId))
    }

    val attachments: ArrayList<Attachment> = gson.fromJson(status.attachments, attachmentArrayListType) ?: arrayListOf()
    val mentions: List<Status.Mention> = gson.fromJson(status.mentions, mentionListType) ?: emptyList()
    val application = gson.fromJson(status.application, Status.Application::class.java)
    val emojis: List<Emoji> = gson.fromJson(status.emojis, emojisListType) ?: emptyList()
    val poll: Poll? = gson.fromJson(status.poll, Poll::class.java)

    val reblog = status.reblogServerId?.let { id ->
        Status(
            id = id,
            url = status.url,
            account = account.toAccount(gson),
            inReplyToId = status.inReplyToId,
            inReplyToAccountId = status.inReplyToAccountId,
            reblog = null,
            content = status.content?.parseAsHtml()?.trimTrailingWhitespace()
                ?: SpannedString(""),
            createdAt = Date(status.createdAt),
            emojis = emojis,
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            reblogged = status.reblogged,
            favourited = status.favourited,
            bookmarked = status.bookmarked,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText!!,
            visibility = status.visibility!!,
            attachments = attachments,
            mentions = mentions,
            application = application,
            pinned = false,
            muted = status.muted,
            poll = poll,
            card = null
        )
    }
    val status = if (reblog != null) {
        Status(
            id = status.serverId,
            url = null, // no url for reblogs
            account = this.reblogAccount!!.toAccount(gson),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = reblog,
            content = SpannedString(""),
            createdAt = Date(status.createdAt), // lie but whatever?
            emojis = listOf(),
            reblogsCount = 0,
            favouritesCount = 0,
            reblogged = false,
            favourited = false,
            bookmarked = false,
            sensitive = false,
            spoilerText = "",
            visibility = status.visibility!!,
            attachments = ArrayList(),
            mentions = listOf(),
            application = null,
            pinned = false,
            muted = status.muted,
            poll = null,
            card = null
        )
    } else {
        Status(
            id = status.serverId,
            url = status.url,
            account = account.toAccount(gson),
            inReplyToId = status.inReplyToId,
            inReplyToAccountId = status.inReplyToAccountId,
            reblog = null,
            content = status.content?.parseAsHtml()?.trimTrailingWhitespace()
                ?: SpannedString(""),
            createdAt = Date(status.createdAt),
            emojis = emojis,
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            reblogged = status.reblogged,
            favourited = status.favourited,
            bookmarked = status.bookmarked,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText!!,
            visibility = status.visibility!!,
            attachments = attachments,
            mentions = mentions,
            application = application,
            pinned = false,
            muted = status.muted,
            poll = poll,
            card = null
        )
    }
    return Either.Right(status)
}
