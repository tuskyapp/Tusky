package com.keylesspalace.tusky.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.IGNORE
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.Single

@Dao
abstract class TimelineDao {

    @Insert(onConflict = REPLACE)
    abstract fun insertAccount(timelineAccountEntity: TimelineAccountEntity): Long

    @Insert(onConflict = REPLACE)
    abstract fun insertStatus(timelineAccountEntity: TimelineStatusEntity): Long


    @Insert(onConflict = IGNORE)
    abstract fun insertStatusIfNotThere(timelineAccountEntity: TimelineStatusEntity): Long

    @Query("""
SELECT s.serverId, s.url, s.timelineUserId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.application, s.reblogServerId,s.reblogAccountId,
s.content, s.attachments, s.poll,
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot',
rb.serverId as 'rb_serverId', rb.timelineUserId 'rb_timelineUserId',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar',
rb.emojis as'rb_emojis', rb.bot as 'rb_bot'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
WHERE s.timelineUserId = :account
AND (CASE WHEN :maxId IS NOT NULL THEN
(LENGTH(s.serverId) < LENGTH(:maxId) OR LENGTH(s.serverId) == LENGTH(:maxId) AND s.serverId < :maxId)
ELSE 1 END)
AND (CASE WHEN :sinceId IS NOT NULL THEN
(LENGTH(s.serverId) > LENGTH(:sinceId) OR LENGTH(s.serverId) == LENGTH(:sinceId) AND s.serverId > :sinceId)
ELSE 1 END)
ORDER BY LENGTH(s.serverId) DESC, s.serverId DESC
LIMIT :limit""")
    abstract fun getStatusesForAccount(account: Long, maxId: String?, sinceId: String?, limit: Int): Single<List<TimelineStatusWithAccount>>


    @Transaction
    open fun insertInTransaction(status: TimelineStatusEntity, account: TimelineAccountEntity,
                                 reblogAccount: TimelineAccountEntity?) {
        insertAccount(account)
        reblogAccount?.let(this::insertAccount)
        insertStatus(status)
    }

    @Query("""DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND
        (LENGTH(serverId) < LENGTH(:maxId) OR LENGTH(serverId) == LENGTH(:maxId) AND serverId < :maxId)
AND
(LENGTH(serverId) > LENGTH(:minId) OR LENGTH(serverId) == LENGTH(:minId) AND serverId > :minId)
    """)
    abstract fun deleteRange(accountId: Long, minId: String, maxId: String)

    @Query("""DELETE FROM TimelineStatusEntity WHERE authorServerId = null
AND timelineUserId = :account AND
(LENGTH(serverId) < LENGTH(:maxId) OR LENGTH(serverId) == LENGTH(:maxId) AND serverId < :maxId)
AND
(LENGTH(serverId) > LENGTH(:sinceId) OR LENGTH(serverId) == LENGTH(:sinceId) AND serverId > :sinceId)
""")
    abstract fun removeAllPlaceholdersBetween(account: Long, maxId: String, sinceId: String)

    @Query("""UPDATE TimelineStatusEntity SET favourited = :favourited
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)""")
    abstract fun setFavourited(accountId: Long, statusId: String, favourited: Boolean)

    @Query("""UPDATE TimelineStatusEntity SET bookmarked = :bookmarked
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)""")
    abstract fun setBookmarked(accountId: Long, statusId: String, bookmarked: Boolean)

    @Query("""UPDATE TimelineStatusEntity SET reblogged = :reblogged
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)""")
    abstract fun setReblogged(accountId: Long, statusId: String, reblogged: Boolean)

    @Query("""DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND
(authorServerId = :userId OR reblogAccountId = :userId)""")
    abstract fun removeAllByUser(accountId: Long, userId: String)

    @Query("DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId")
    abstract fun removeAllForAccount(accountId: Long)

    @Query("DELETE FROM TimelineAccountEntity WHERE timelineUserId = :accountId")
    abstract fun removeAllUsersForAccount(accountId: Long)

    @Query("""DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId
AND serverId = :statusId""")
    abstract fun delete(accountId: Long, statusId: String)

    @Query("""DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId
AND authorServerId != :accountServerId AND createdAt < :olderThan""")
    abstract fun cleanup(accountId: Long, accountServerId: String, olderThan: Long)

    @Query("""UPDATE TimelineStatusEntity SET poll = :poll
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)""")
    abstract fun setVoted(accountId: Long, statusId: String, poll: String)
}