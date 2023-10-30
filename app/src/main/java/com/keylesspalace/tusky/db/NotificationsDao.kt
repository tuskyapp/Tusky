/* Copyright 2023 Tusky Contributors
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
abstract class NotificationsDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertNotification(notificationEntity: NotificationEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertAccount(notificationsAccountEntity: NotificationAccountEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertStatus(notificationStatusEntity: NotificationStatusEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertReport(notificationReportDataEntity: NotificationReportEntity): Long

    @Query(
        """
SELECT n.tuskyAccountId, n.type, n.id, n.loading,
a.id as 'a_id', a.tuskyAccountId as 'a_tuskyAccountId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot',
s.id as 's_id', s.url as 's_url', s.tuskyAccountId as 's_tuskyAccountId',
s.authorServerId as 's_authorServerId', s.inReplyToId as 's_inReplyToId', s.inReplyToAccountId as 's_inReplyToAccountId',
s.content as 's_content', s.createdAt as 's_createdAt', s.editedAt as 's_editedAt', s.emojis as 's_emojis', s.reblogsCount as 's_reblogsCount',
s.favouritesCount as 's_favouritesCount', s.repliesCount as 's_repliesCount', s.reblogged as 's_reblogged', s.favourited as 's_favourited',
s.bookmarked as 's_bookmarked', s.sensitive as 's_sensitive', s.spoilerText as 's_spoilerText', s.visibility as 's_visibility',
s.mentions as 's_mentions', s.tags as 's_tags', s.application as 's_application', s.reblogServerId as 's_reblogServerId',
s.reblogAccountId as 's_reblogAccountId', s.content as 's_content', s.attachments as 's_attachments', s.poll as 's_poll',
s.card as 's_card', s.muted as 's_muted', s.expanded as 's_expanded', s.contentShowing as 's_contentShowing', s.contentCollapsed as 's_contentCollapsed',
s.pinned as 's_pinned', s.language as 's_language', s.filtered as 's_filtered',
sa.id as 'sa_id', sa.tuskyAccountId as 'sa_tuskyAccountId',
sa.localUsername as 'sa_localUsername', sa.username as 'sa_username',
sa.displayName as 'sa_displayName', sa.url as 'sa_url', sa.avatar as 'sa_avatar',
sa.emojis as 'sa_emojis', sa.bot as 'sa_bot',
r.id as 'r_id', r.tuskyAccountId as 'r_tuskyAccountId',
r.category as 'r_category', r.statusIds as 'r_statusIds',
r.createdAt as 'r_createdAt',
ra.id as 'ra_id', ra.tuskyAccountId as 'ra_tuskyAccountId',
ra.localUsername as 'ra_localUsername', ra.username as 'ra_username',
ra.displayName as 'ra_displayName', ra.url as 'a_url', ra.avatar as 'ra_avatar',
ra.emojis as 'ra_emojis', ra.bot as 'ra_bot'
FROM NotificationEntity n
LEFT JOIN NotificationAccountEntity a ON (n.tuskyAccountId = a.tuskyAccountId AND n.accountId = a.id)
LEFT JOIN NotificationStatusEntity s ON (n.tuskyAccountId = s.tuskyAccountId AND n.statusId = s.id)
LEFT JOIN NotificationAccountEntity sa ON (n.tuskyAccountId = sa.tuskyAccountId AND s.authorServerId = sa.id)
LEFT JOIN NotificationReportEntity r ON (n.tuskyAccountId = r.tuskyAccountId AND n.reportId = r.id)
LEFT JOIN NotificationAccountEntity ra ON (n.tuskyAccountId = ra.tuskyAccountId AND r.targetAccountId = ra.id)
WHERE s.tuskyAccountId = :account
ORDER BY LENGTH(n.id) DESC, n.id DESC"""
    )
    abstract fun getNotifications(account: Long): PagingSource<Int, NotificationDataEntity>

    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :accountId AND
        (LENGTH(id) < LENGTH(:maxId) OR LENGTH(id) == LENGTH(:maxId) AND id <= :maxId)
AND
(LENGTH(id) > LENGTH(:minId) OR LENGTH(id) == LENGTH(:minId) AND id >= :minId)
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

    @Query("UPDATE TimelineStatusEntity SET filtered = NULL WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)")
    abstract suspend fun clearWarning(accountId: Long, statusId: String): Int

    @Query("SELECT id FROM NotificationEntity WHERE tuskyAccountId = :accountId ORDER BY LENGTH(id) DESC, id DESC LIMIT 1")
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
