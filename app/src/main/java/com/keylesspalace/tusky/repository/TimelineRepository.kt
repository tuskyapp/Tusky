package com.keylesspalace.tusky.repository

import android.text.SpannedString
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keylesspalace.tusky.db.*
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HtmlUtils
import io.reactivex.Single
import java.util.*

interface TimelineRepository {
    fun getStatuses(maxId: String?, sinceId: String?, limit: Int): Single<List<Status>>
}

class TimelineRepostiryImpl(
        private val timelineDao: TimelineDao,
        private val mastodonApi: MastodonApi,
        private val accountManager: AccountManager,
        private val gson: Gson
) : TimelineRepository {

    private val emojisToken = genericType<List<Emoji>>()
    private val attachmentsToken = genericType<List<Attachment>>()
    private val mentionsToken = genericType<List<Status.Mention>>()

    override fun getStatuses(maxId: String?, sinceId: String?, limit: Int): Single<List<Status>> {
        val acc = accountManager.activeAccount ?: throw IllegalStateException()
        val accountId = acc.id
        val instance = acc.domain
        return timelineDao.getStatusesForAccount(accountId, maxId, sinceId, limit)
                .flatMap { dbResult ->
                    if (dbResult.size < 2) {
                        mastodonApi.homeTimelineSingle(maxId, sinceId, limit)
                                .doAfterSuccess { serverResult ->
                                    for (status in serverResult) {
                                        timelineDao.insertAccount(
                                                status.account.toEntity(instance)
                                        )
                                        status.reblog?.account
                                                ?.toEntity(instance)
                                                ?.let(timelineDao::insertAccount)
                                        timelineDao.insertStatus(
                                                status.toEntity(accountId, instance)
                                        )
                                    }
                                }
                    } else {
                        Single.just(dbResult.map { it.toStatus() })
                    }
                }
    }

    private fun Account.toEntity(instance: String): TimelineAccountEntity {
        return TimelineAccountEntity(
                serverId = id,
                instance = instance,
                localUsername = localUsername,
                username = username,
                displayName = displayName,
                url = url,
                avatar = avatar
        )
    }

    private fun TimelineAccountEntity.toAccount(): Account {
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
                source = null
        )
    }

    inline fun <reified T> genericType() = object : TypeToken<T>() {}.type

    private fun TimelineStatusWithAccount.toStatus(): Status {

        val emojis = status.emojis?.let {
            gson.fromJson<List<Emoji>>(it, emojisToken)
        } ?: listOf()

        val attachments = status.attachments?.let {
            gson.fromJson<List<Attachment>>(it, attachmentsToken)
        } ?: listOf()

        val mentions = status.mentions?.let {
            gson.fromJson<List<Status.Mention>>(it, mentionsToken)
        } ?: listOf()

        val application = status.application?.let {
            gson.fromJson(it, Status.Application::class.java)
        }

        val contentStatus = Status(
                // if it's a reblog, fetch embedded id from the field, otherwise it's just an id
                id = if (status.realServerId.isNotEmpty()) status.realServerId else status.serverId,
                url = status.url,
                account = (reblogAccount ?: account).toAccount(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = HtmlUtils.fromHtml(status.content),
                createdAt = Date(status.createdAt),
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                reblogged = status.reblogged,
                favourited = status.favourited,
                sensitive = status.sensitive,
                spoilerText = status.spoilerText,
                visibility = status.visibility,
                attachments = attachments,
                mentions = mentions,
                application = application
        )
        return if (status.reblogUri != null) {
            Status(
                    // it's a reblog, id is in the serverId field
                    id = status.serverId,
                    url = status.reblogUri!!,
                    account =  account.toAccount(),
                    reblog = contentStatus,
                    inReplyToId = null,
                    inReplyToAccountId = null,
                    content = SpannedString(""),
                    createdAt = contentStatus.createdAt, // Fake and it doesn't matter
                    emojis = listOf(),
                    reblogsCount = 0,
                    favourited = false,
                    reblogged = false,
                    sensitive = false,
                    favouritesCount = 0,
                    spoilerText = "",
                    visibility = status.visibility,
                    attachments = listOf(),
                    mentions = listOf(),
                    application = null
            )
        } else {
            contentStatus
        }
    }

    private fun Status.toEntity(timelineUserId: Long, instance: String): TimelineStatusEntity {
        val actionable = actionableStatus
        return TimelineStatusEntity(
                serverId = reblog?.id ?: id,
                url = actionable.url,
                instance = instance,
                timelineUserId = timelineUserId,
                authorServerId = actionable.account.id,
                inReplyToId = actionable.inReplyToId,
                inReplyToAccountId = actionable.inReplyToAccountId,
                content = HtmlUtils.toHtml(actionable.content),
                createdAt = actionable.createdAt.time,
                emojis = gson.toJson(emojis),
                reblogsCount = actionable.reblogsCount,
                favouritesCount = actionable.favouritesCount,
                reblogged = actionable.reblogged,
                favourited = actionable.favourited,
                sensitive = actionable.sensitive,
                spoilerText = actionable.spoilerText,
                visibility = actionable.visibility,
                attachments = gson.toJson(attachments),
                mentions = gson.toJson(mentions),
                application = gson.toJson(application),
                realServerId = if (reblog != null) id else "",
                reblogUri = reblog?.url,
                reblogAccountId = if (reblog != null) account.id else ""
        )
    }
}