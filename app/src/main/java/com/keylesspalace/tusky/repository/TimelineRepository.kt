package com.keylesspalace.tusky.repository

import android.text.Html
import android.text.SpannedString
import com.keylesspalace.tusky.db.*
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.Single
import java.util.*

interface TimelineRepository {
    fun getStatuses(maxId: String?, sinceId: String?, limit: Int): Single<List<Status>>
}

class TimelineRepostiryImpl(
        private val timelineDao: TimelineDao,
        private val mastodonApi: MastodonApi,
        private val accountManager: AccountManager
) : TimelineRepository {
    override fun getStatuses(maxId: String?, sinceId: String?, limit: Int): Single<List<Status>> {
        val acc = accountManager.activeAccount ?: throw IllegalStateException()
        val accountId = acc.id
        val instance = acc.domain
        return timelineDao.getStatusesForAccount(accountId, maxId, sinceId, limit)
                .flatMap { dbResult ->
                    if (dbResult.isEmpty()) {
                        mastodonApi.homeTimelineSingle(maxId, sinceId, limit)
                                .doAfterSuccess { serverResult ->
                                    for (status in serverResult) {
                                        val author = timelineDao.insertAccount(
                                                status.account.toEntity(instance)
                                        )
                                        val reblogAuthor = status.reblog?.account
                                                ?.toEntity(instance)
                                                ?.let(timelineDao::insertAccount) ?: 0
                                        timelineDao.insertStatus(
                                                status.toEntity(accountId, author, instance,
                                                        reblogAuthor)
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
                id = 0,
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

    private fun TimelineStatusWithAccount.toStatus(): Status {
        return Status(
                id = status.serverId,
                url = status.url,
                account = account.toAccount(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null, // TODO
                content = Html.fromHtml(status.content),
                createdAt = Date(status.createdAt),
                emojis = listOf(), // TODO
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                reblogged = status.reblogged,
                favourited = status.favourited,
                sensitive = status.sensitive,
                spoilerText = status.spoilerText,
                visibility = status.visibility,
                attachments = listOf(), // TODO
                mentions = arrayOf(), // TODO
                application = null // TODO
        )
    }

    private fun Status.toEntity(timelineUserId: Long, author: Long, instance: String,
                                reblogAccountId: Long): TimelineStatusEntity {
        val actionable = actionableStatus
        return TimelineStatusEntity(
                id = 0,
                serverId = actionable.id,
                url = actionable.url,
                instance = instance,
                timelineUserId = timelineUserId,
                authorLocalId = author,
                authorServerId = actionable.account.id,
                inReplyToId = actionable.inReplyToId,
                inReplyToAccountId = actionable.inReplyToAccountId,
                content = actionable.content.toString(),
                createdAt = actionable.createdAt.time,
                emojis = null, // TODO
                reblogsCount = actionable.reblogsCount,
                favouritesCount = actionable.favouritesCount,
                reblogged = actionable.reblogged,
                favourited = actionable.favourited,
                sensitive = actionable.sensitive,
                spoilerText = actionable.spoilerText,
                visibility = actionable.visibility,
                attachments = null, // TODO
                mentions = null, // TODO
                application = null, // TODO
                reblogServerId = reblog?.id,
                reblogUri = reblog?.url,
                reblogAccountId = reblogAccountId
        )
    }
}