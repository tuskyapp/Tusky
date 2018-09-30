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
import java.io.IOException
import java.util.*

interface TimelineRepository {
    fun getStatuses(maxId: String?, sinceId: String?, limit: Int,
                    offlineOnly: Boolean): Single<List<Status>>
}

class TimelineRepostiryImpl(
        private val timelineDao: TimelineDao,
        private val mastodonApi: MastodonApi,
        private val accountManager: AccountManager,
        private val gson: Gson
) : TimelineRepository {
    override fun getStatuses(maxId: String?, sinceId: String?, limit: Int,
                             offlineOnly: Boolean): Single<List<Status>> {
        val acc = accountManager.activeAccount ?: throw IllegalStateException()
        val accountId = acc.id
        val instance = acc.domain

        return if (offlineOnly) {
            this.getStatusesFromDb(accountId, maxId, sinceId, limit)
        } else {
            mastodonApi.homeTimelineSingle(maxId, sinceId, limit)
                    .doAfterSuccess { statuses ->
                        this.saveStatusesToDb(instance, accountId, statuses)
                    }
                    .onErrorResumeNext { error ->
                        if (error is IOException) {
                            this.getStatusesFromDb(accountId, maxId, sinceId, limit)
                        } else {
                            Single.error(error)
                        }
                    }
        }
    }

    private fun getStatusesFromDb(accountId: Long, maxId: String?, sinceId: String?,
                                  limit: Int): Single<List<Status>> {
        return timelineDao.getStatusesForAccount(accountId, maxId, sinceId, limit)
                .map { statuses -> statuses.map { it.toStatus() } }
    }

    private fun saveStatusesToDb(instance: String, accountId: Long, statuses: List<Status>) {
        for (status in statuses) {
            timelineDao.insertAccount(
                    status.account.toEntity(instance, accountId)
            )
            status.reblog?.account
                    ?.toEntity(instance, accountId)
                    ?.let(timelineDao::insertAccount)
            timelineDao.insertStatus(
                    status.toEntity(accountId, instance)
            )
        }
    }

    private fun Account.toEntity(instance: String, accountId: Long): TimelineAccountEntity {
        return TimelineAccountEntity(
                serverId = id,
                timelineUserId = accountId,
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
                source = null,
                // TODO actually fill these fields
                bot = false,
                emojis = null,
                fields = null,
                moved = null
        )
    }

    private fun TimelineStatusWithAccount.toStatus(): Status {
        val attachments: List<Attachment> = gson.fromJson(status.attachments,
                object : TypeToken<List<Attachment>>() {}.type) ?: listOf()
        val mentions: Array<Status.Mention> = gson.fromJson(status.mentions,
                Array<Status.Mention>::class.java) ?: arrayOf()
        val application = gson.fromJson(status.application, Status.Application::class.java)
        val emojis: List<Emoji> = gson.fromJson(status.emojis,
                object : TypeToken<List<Emoji>>() {}.type) ?: listOf()

        val reblog = status.reblogServerId?.let { id ->
            Status(
                    id = id,
                    url = status.url,
                    account = account.toAccount(),
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
        }
        return if (reblog != null) {
            Status(
                    id = status.serverId,
                    url = null, // no url for reblogs
                    account = this.reblogAccount!!.toAccount(),
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
                    sensitive = false,
                    spoilerText = "",
                    visibility = status.visibility,
                    attachments = listOf(),
                    mentions = arrayOf(),
                    application = null
            )
        } else {
            Status(
                    id = status.serverId,
                    url = status.url,
                    account = account.toAccount(),
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
        }
    }

    private fun Status.toEntity(timelineUserId: Long, instance: String): TimelineStatusEntity {
        val actionable = actionableStatus
        return TimelineStatusEntity(
                serverId = this.id,
                url = actionable.url!!,
                instance = instance,
                timelineUserId = timelineUserId,
                authorServerId = actionable.account.id,
                inReplyToId = actionable.inReplyToId,
                inReplyToAccountId = actionable.inReplyToAccountId,
                content = HtmlUtils.toHtml(actionable.content),
                createdAt = actionable.createdAt.time,
                emojis = actionable.emojis.let(gson::toJson),
                reblogsCount = actionable.reblogsCount,
                favouritesCount = actionable.favouritesCount,
                reblogged = actionable.reblogged,
                favourited = actionable.favourited,
                sensitive = actionable.sensitive,
                spoilerText = actionable.spoilerText,
                visibility = actionable.visibility,
                attachments = actionable.attachments.let(gson::toJson),
                mentions = actionable.mentions.let(gson::toJson),
                application = actionable.let(gson::toJson),
                reblogServerId = reblog?.let { this.id },
                reblogAccountId = reblog?.let { this.account.id }
        )
    }
}