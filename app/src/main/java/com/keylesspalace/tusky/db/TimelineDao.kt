package com.keylesspalace.tusky.db

import android.arch.persistence.room.*
import android.arch.persistence.room.OnConflictStrategy.IGNORE
import android.arch.persistence.room.OnConflictStrategy.REPLACE
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
s.authorServerId, s.instance, s.inReplyToId, s.inReplyToAccountId, s.createdAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.reblogged, s.favourited, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.application, s.reblogServerId,s.reblogAccountId,
s.content, s.attachments,
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId', a.instance as 'a_instance',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
rb.serverId as 'rb_serverId', rb.timelineUserId 'rb_timelineUserId', rb.instance as 'rb_instance',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
WHERE s.timelineUserId = :account
AND (CASE WHEN :maxId IS NOT NULL THEN s.serverId < :maxId ELSE 1 END)
AND (CASE WHEN :sinceId IS NOT NULL THEN s.serverId > :sinceId ELSE 1 END)
ORDER BY s.serverId DESC
LIMIT :limit""")
    abstract fun getStatusesForAccount(account: Long, maxId: String?, sinceId: String?, limit: Int): Single<List<TimelineStatusWithAccount>>


    @Transaction
    open fun insertInTransaction(status: TimelineStatusEntity, account: TimelineAccountEntity,
                                 reblogAccount: TimelineAccountEntity?) {
        insertAccount(account)
        reblogAccount?.let(this::insertAccount)
        insertStatus(status)
    }

    @Query("""DELETE FROM TimelineStatusEntity WHERE authorServerId = null
AND timelineUserId = :acccount AND serverId > :sinceId AND serverId < :maxId""")
    abstract fun removeAllPlaceholdersBetween(acccount: Long, maxId: String, sinceId: String)

    @Query("""UPDATE TimelineStatusEntity SET favourited = :favourited
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId - :statusId)""")
    abstract fun setFavourited(accountId: Long, statusId: String, favourited: Boolean)


    @Query("""UPDATE TimelineStatusEntity SET reblogged = :reblogged
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId - :statusId)""")
    abstract fun setReblogged(accountId: Long, statusId: String, reblogged: Boolean)

    @Query("""DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND
(authorServerId = :userId OR reblogAccountId = :userId)""")
    abstract fun removeAllByUser(accountId: Long, userId: String)

    @Query("""DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId
AND serverId = :statusId""")
    abstract fun delete(accountId: Long, statusId: String)
}