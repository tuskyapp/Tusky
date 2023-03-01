/* Copyright 2021 Tusky Contributors
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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query

@Dao
abstract class TimelineDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertAccount(timelineAccountEntity: TimelineAccountEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertStatus(timelineStatusEntity: TimelineStatusEntity): Long

    @Query(
        """
SELECT s.serverId, s.url, s.timelineUserId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt, s.editedAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.repliesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.tags, s.application, s.reblogServerId,s.reblogAccountId,
s.content, s.attachments, s.poll, s.card, s.muted, s.expanded, s.contentShowing, s.contentCollapsed, s.pinned, s.language,
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot',
rb.serverId as 'rb_serverId', rb.timelineUserId 'rb_timelineUserId',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar',
rb.emojis as 'rb_emojis', rb.bot as 'rb_bot'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
WHERE s.timelineUserId = :account
ORDER BY LENGTH(s.serverId) DESC, s.serverId DESC"""
    )
    abstract fun getStatuses(account: Long): PagingSource<Int, TimelineStatusWithAccount>

    @Query(
        """
SELECT s.serverId, s.url, s.timelineUserId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt, s.editedAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.repliesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.tags, s.application, s.reblogServerId,s.reblogAccountId,
s.content, s.attachments, s.poll, s.card, s.muted, s.expanded, s.contentShowing, s.contentCollapsed, s.pinned, s.language,
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot',
rb.serverId as 'rb_serverId', rb.timelineUserId 'rb_timelineUserId',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar',
rb.emojis as 'rb_emojis', rb.bot as 'rb_bot'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
WHERE (s.serverId = :statusId OR s.reblogServerId = :statusId)
AND s.authorServerId IS NOT NULL"""
    )
    abstract suspend fun getStatus(statusId: String): TimelineStatusWithAccount?

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND
        (LENGTH(serverId) < LENGTH(:maxId) OR LENGTH(serverId) == LENGTH(:maxId) AND serverId <= :maxId)
AND
(LENGTH(serverId) > LENGTH(:minId) OR LENGTH(serverId) == LENGTH(:minId) AND serverId >= :minId)
    """
    )
    abstract suspend fun deleteRange(accountId: Long, minId: String, maxId: String): Int

    @Query(
        """UPDATE TimelineStatusEntity SET favourited = :favourited
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setFavourited(accountId: Long, statusId: String, favourited: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET bookmarked = :bookmarked
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setBookmarked(accountId: Long, statusId: String, bookmarked: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET reblogged = :reblogged
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setReblogged(accountId: Long, statusId: String, reblogged: Boolean)

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND
(authorServerId = :userId OR reblogAccountId = :userId)"""
    )
    abstract suspend fun removeAllByUser(accountId: Long, userId: String)

    /**
     * Removes everything in the TimelineStatusEntity and TimelineAccountEntity tables for one user account
     * @param accountId id of the account for which to clean tables
     */
    suspend fun removeAll(accountId: Long) {
        removeAllStatuses(accountId)
        removeAllAccounts(accountId)
    }

    @Query("DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId")
    abstract suspend fun removeAllStatuses(accountId: Long)

    @Query("DELETE FROM TimelineAccountEntity WHERE timelineUserId = :accountId")
    abstract suspend fun removeAllAccounts(accountId: Long)

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId
AND serverId = :statusId"""
    )
    abstract suspend fun delete(accountId: Long, statusId: String)

    /**
     * Cleans the TimelineStatusEntity and TimelineAccountEntity tables from old entries.
     * @param accountId id of the account for which to clean tables
     * @param limit how many statuses to keep
     */
    suspend fun cleanup(accountId: Long, limit: Int) {
        cleanupStatuses(accountId, limit)
        cleanupAccounts(accountId)
    }

    /**
     * Cleans the TimelineStatusEntity table from old status entries.
     * @param accountId id of the account for which to clean statuses
     * @param limit how many statuses to keep
     */
    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND serverId NOT IN
        (SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT :limit)
    """
    )
    abstract suspend fun cleanupStatuses(accountId: Long, limit: Int)

    /**
     * Cleans the TimelineAccountEntity table from accounts that are no longer referenced in the TimelineStatusEntity table
     * @param accountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """DELETE FROM TimelineAccountEntity WHERE timelineUserId = :accountId AND serverId NOT IN
        (SELECT authorServerId FROM TimelineStatusEntity WHERE timelineUserId = :accountId)
        AND serverId NOT IN
        (SELECT reblogAccountId FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND reblogAccountId IS NOT NULL)"""
    )
    abstract suspend fun cleanupAccounts(accountId: Long)

    @Query(
        """UPDATE TimelineStatusEntity SET poll = :poll
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setVoted(accountId: Long, statusId: String, poll: String)

    @Query(
        """UPDATE TimelineStatusEntity SET expanded = :expanded
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setExpanded(accountId: Long, statusId: String, expanded: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET contentShowing = :contentShowing
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setContentShowing(accountId: Long, statusId: String, contentShowing: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET contentCollapsed = :contentCollapsed
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setContentCollapsed(accountId: Long, statusId: String, contentCollapsed: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET pinned = :pinned
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)"""
    )
    abstract suspend fun setPinned(accountId: Long, statusId: String, pinned: Boolean)

    @Query(
        """DELETE FROM TimelineStatusEntity
WHERE timelineUserId = :accountId AND authorServerId IN (
SELECT serverId FROM TimelineAccountEntity WHERE username LIKE '%@' || :instanceDomain
AND timelineUserId = :accountId
)"""
    )
    abstract suspend fun deleteAllFromInstance(accountId: Long, instanceDomain: String)

    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT 1")
    abstract suspend fun getTopId(accountId: Long): String?

    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND authorServerId IS NULL ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT 1")
    abstract suspend fun getTopPlaceholderId(accountId: Long): String?

    /**
     * Returns the id directly above [serverId], or null if [serverId] is the id of the top status
     */
    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND (LENGTH(:serverId) < LENGTH(serverId) OR (LENGTH(:serverId) = LENGTH(serverId) AND :serverId < serverId)) ORDER BY LENGTH(serverId) ASC, serverId ASC LIMIT 1")
    abstract suspend fun getIdAbove(accountId: Long, serverId: String): String?

    /**
     * Returns the ID directly below [serverId], or null if [serverId] is the ID of the bottom
     * status
     */
    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND (LENGTH(:serverId) > LENGTH(serverId) OR (LENGTH(:serverId) = LENGTH(serverId) AND :serverId > serverId)) ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT 1")
    abstract suspend fun getIdBelow(accountId: Long, serverId: String): String?

    /**
     * Returns the id of the next placeholder after [serverId]
     */
    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND authorServerId IS NULL AND (LENGTH(:serverId) > LENGTH(serverId) OR (LENGTH(:serverId) = LENGTH(serverId) AND :serverId > serverId)) ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT 1")
    abstract suspend fun getNextPlaceholderIdAfter(accountId: Long, serverId: String): String?

    @Query("SELECT COUNT(*) FROM TimelineStatusEntity WHERE timelineUserId = :accountId")
    abstract suspend fun getStatusCount(accountId: Long): Int

    /** Developer tools: Find N most recent status IDs */
    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT :count")
    abstract suspend fun getMostRecentNStatusIds(accountId: Long, count: Int): List<String>

    /** Developer tools: Convert a status to a placeholder */
    @Query("UPDATE TimelineStatusEntity SET authorServerId = NULL WHERE serverId = :serverId")
    abstract suspend fun convertStatustoPlaceholder(serverId: String)
}
