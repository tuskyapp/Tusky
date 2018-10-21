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
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.HtmlUtils
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.math.BigInteger
import java.util.*
import java.util.concurrent.TimeUnit

data class Placeholder(val id: String)

typealias TimelineStatus = Either<Placeholder, Status>

interface TimelineRepository {
    fun getStatuses(maxId: String?, sinceId: String?, limit: Int,
                    offlineOnly: Boolean): Single<out List<TimelineStatus>>

    companion object {
        val CLEANUP_INTERVAL = TimeUnit.DAYS.toMillis(14)
    }
}

class TimelineRepostiryImpl(
        private val timelineDao: TimelineDao,
        private val mastodonApi: MastodonApi,
        private val accountManager: AccountManager,
        private val gson: Gson
) : TimelineRepository {

    init {
        this.cleanup()
    }

    override fun getStatuses(maxId: String?, sinceId: String?, limit: Int,
                             offlineOnly: Boolean): Single<out List<TimelineStatus>> {
        val acc = accountManager.activeAccount ?: throw IllegalStateException()
        val accountId = acc.id
        val instance = acc.domain

        return if (offlineOnly) {
            this.getStatusesFromDb(accountId, maxId, sinceId, limit)
        } else {
            getStatusesFromNetwork(maxId, sinceId, limit, instance, accountId)
        }
    }

    private fun getStatusesFromNetwork(maxId: String?, sinceId: String?, limit: Int,
                                       instance: String, accountId: Long
    ): Single<out List<TimelineStatus>> {
        val maxIdInc = maxId?.let { this.incId(it, 1) }
        val sinceIdDec = sinceId?.let { this.incId(it, -1) }
        return mastodonApi.homeTimelineSingle(maxIdInc, sinceIdDec, limit + 2)
                .doAfterSuccess { statuses ->
                    this.saveStatusesToDb(instance, accountId, statuses, maxId, sinceId)
                }
                .map { statuses -> this.removePlaceholdersAndMap(statuses, maxId, sinceId) }
                .flatMap { statuses ->
                    this.addFromDbIfNeeded(accountId, statuses, maxId, sinceId, limit)
                }
                .onErrorResumeNext { error ->
                    if (error is IOException) {
                        this.getStatusesFromDb(accountId, maxId, sinceId, limit)
                    } else {
                        Single.error(error)
                    }
                }
    }

    private fun removePlaceholdersAndMap(statuses: List<Status>, maxId: String?,
                                         sinceId: String?
    ): List<Either.Right<Placeholder, Status>> {
        val statusesCopy = statuses.toMutableList()

        // Remove first and last statuses if they were used used just for overlap
        if (maxId != null && statusesCopy.firstOrNull()?.id == maxId) {
            statusesCopy.removeAt(0)
        }
        if (sinceId != null && statusesCopy.lastOrNull()?.id == sinceId) {
            statusesCopy.removeAt(statuses.size - 1)
        }

        return statusesCopy.map { s -> Either.Right<Placeholder, Status>(s) }
    }

    private fun addFromDbIfNeeded(accountId: Long, statuses: List<Either<Placeholder, Status>>,
                          maxId: String?, sinceId: String?, limit: Int
    ): Single<List<TimelineStatus>>? {
        return if (maxId != null && statuses.size < 2) {
            val newMaxID = if (statuses.isEmpty()) {
                maxId
            } else {
                // It's statuses from network. They're always Right
                statuses.last().asRight().id
            }
            this.getStatusesFromDb(accountId, newMaxID, sinceId, limit)
                    .map { fromDb ->
                        // If it's just placeholders and less than limit (so we exhausted both
                        // db and server at this point)
                        if (fromDb.size < limit && fromDb.all { !it.isRight() }) {
                            statuses
                        } else {
                            statuses + fromDb
                        }
                    }
        } else {
            Single.just(statuses)
        }
    }

    private fun getStatusesFromDb(accountId: Long, maxId: String?, sinceId: String?,
                                  limit: Int): Single<out List<TimelineStatus>> {
        return timelineDao.getStatusesForAccount(accountId, maxId, sinceId, limit)
                .subscribeOn(Schedulers.io())
                .map { statuses -> statuses.map { it.toStatus() } }
    }

    private fun saveStatusesToDb(instance: String, accountId: Long, statuses: List<Status>,
                                 maxId: String?, sinceId: String?) {
        Single.fromCallable {
            val (prepend, append) = calculatePlaceholders(maxId, sinceId, statuses)

            if (prepend != null) {
                timelineDao.insertStatusIfNotThere(prepend.toEntity(accountId))
            }

            if (append != null) {
                timelineDao.insertStatusIfNotThere(append.toEntity(accountId))
            }

            for (status in statuses) {
                timelineDao.insertInTransaction(
                        status.toEntity(accountId, instance),
                        status.account.toEntity(instance, accountId),
                        status.reblog?.account?.toEntity(instance, accountId)
                )
            }

            // There may be placeholders which we thought could be from our TL but they are not
            if (statuses.size > 2) {
                timelineDao.removeAllPlaceholdersBetween(accountId, statuses.first().id,
                        statuses.last().id)
            } else if (maxId != null && sinceId != null) {
                timelineDao.removeAllPlaceholdersBetween(accountId, maxId, sinceId)
            }
        }
                .subscribeOn(Schedulers.io())
                .subscribe()

    }

    private fun calculatePlaceholders(maxId: String?, sinceId: String?,
                                      statuses: List<Status>
    ): Pair<Placeholder?, Placeholder?> {
        if (statuses.isEmpty()) return null to null

        val firstId = statuses.first().id
        val prepend = if (maxId != null) {
            if (maxId > firstId) {
                val decMax = this.incId(maxId, -1)
                if (decMax != firstId) {
                    Placeholder(decMax)
                } else null
            } else null
        } else {
            // Placeholders never overwrite real values so it's safe
            Placeholder(incId(firstId, 1))
        }

        val lastId = statuses.last().id
        val append = if (sinceId != null) {
            if (sinceId < lastId) {
                val incSince = this.incId(sinceId, 1)
                if (incSince != lastId) {
                    Placeholder(incSince)
                } else null
            } else null
        } else {
            // Placeholders never overwrite real values so it's safe
            Placeholder(incId(lastId, -1))
        }

        return prepend to append
    }

    private fun cleanup() {
        Single.fromCallable {
            val olderThan = System.currentTimeMillis() - TimelineRepository.CLEANUP_INTERVAL
            for (account in accountManager.getAllAccountsOrderedByActive()) {
                timelineDao.cleanup(account.id, account.accountId, olderThan)
            }
        }
                .subscribeOn(Schedulers.io())
                .subscribe()
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

    private fun TimelineStatusWithAccount.toStatus(): TimelineStatus {
        if (this.status.authorServerId == null) {
            return Either.Left(Placeholder(this.status.serverId))
        }

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
                    spoilerText = status.spoilerText!!,
                    visibility = status.visibility!!,
                    attachments = attachments,
                    mentions = mentions,
                    application = application,
                    pinned = false

            )
        }
        val status = if (reblog != null) {
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
                    visibility = status.visibility!!,
                    attachments = listOf(),
                    mentions = arrayOf(),
                    application = null,
                    pinned = false
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
                    spoilerText = status.spoilerText!!,
                    visibility = status.visibility!!,
                    attachments = attachments,
                    mentions = mentions,
                    application = application,
                    pinned = false
            )
        }
        return Either.Right(status)
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
                reblogServerId = reblog?.id,
                reblogAccountId = reblog?.let { this.account.id }
        )
    }

    private fun Placeholder.toEntity(timelineUserId: Long): TimelineStatusEntity {
        return TimelineStatusEntity(
                serverId = this.id,
                url = null,
                instance = null,
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
                sensitive = false,
                spoilerText = null,
                visibility = null,
                attachments = null,
                mentions = null,
                application = null,
                reblogServerId = null,
                reblogAccountId = null

        )
    }

    private fun incId(id: String, value: Long): String {
        return BigInteger(id).add(BigInteger.valueOf(value)).toString()
    }
}