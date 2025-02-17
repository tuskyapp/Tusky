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

package com.keylesspalace.tusky.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.keylesspalace.tusky.db.entity.NotificationDataEntity
import com.keylesspalace.tusky.db.entity.NotificationEntity
import com.keylesspalace.tusky.db.entity.NotificationReportEntity

@Dao
abstract class NotificationsDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertNotification(notificationEntity: NotificationEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertReport(notificationReportDataEntity: NotificationReportEntity): Long

    @Query(
        """
SELECT n.tuskyAccountId, n.type, n.id, n.loading, n.event, n.moderationWarning,
a.serverId as 'a_serverId', a.tuskyAccountId as 'a_tuskyAccountId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot',
s.serverId as 's_serverId', s.url as 's_url', s.tuskyAccountId as 's_tuskyAccountId',
s.authorServerId as 's_authorServerId', s.inReplyToId as 's_inReplyToId', s.inReplyToAccountId as 's_inReplyToAccountId',
s.content as 's_content', s.createdAt as 's_createdAt', s.editedAt as 's_editedAt', s.emojis as 's_emojis', s.reblogsCount as 's_reblogsCount',
s.favouritesCount as 's_favouritesCount', s.repliesCount as 's_repliesCount', s.reblogged as 's_reblogged', s.favourited as 's_favourited',
s.bookmarked as 's_bookmarked', s.sensitive as 's_sensitive', s.spoilerText as 's_spoilerText', s.visibility as 's_visibility',
s.mentions as 's_mentions', s.tags as 's_tags', s.application as 's_application', s.content as 's_content', s.attachments as 's_attachments', s.poll as 's_poll',
s.card as 's_card', s.muted as 's_muted', s.expanded as 's_expanded', s.contentShowing as 's_contentShowing', s.contentCollapsed as 's_contentCollapsed',
s.pinned as 's_pinned', s.language as 's_language', s.filtered as 's_filtered',
sa.serverId as 'sa_serverId', sa.tuskyAccountId as 'sa_tuskyAccountId',
sa.localUsername as 'sa_localUsername', sa.username as 'sa_username',
sa.displayName as 'sa_displayName', sa.url as 'sa_url', sa.avatar as 'sa_avatar',
sa.emojis as 'sa_emojis', sa.bot as 'sa_bot',
r.serverId as 'r_serverId', r.tuskyAccountId as 'r_tuskyAccountId',
r.category as 'r_category', r.statusIds as 'r_statusIds',
r.createdAt as 'r_createdAt', r.targetAccountId as 'r_targetAccountId',
ra.serverId as 'ra_serverId', ra.tuskyAccountId as 'ra_tuskyAccountId',
ra.localUsername as 'ra_localUsername', ra.username as 'ra_username',
ra.displayName as 'ra_displayName', ra.url as 'ra_url', ra.avatar as 'ra_avatar',
ra.emojis as 'ra_emojis', ra.bot as 'ra_bot'
FROM NotificationEntity n
LEFT JOIN TimelineAccountEntity a ON (n.tuskyAccountId = a.tuskyAccountId AND n.accountId = a.serverId)
LEFT JOIN TimelineStatusEntity s ON (n.tuskyAccountId = s.tuskyAccountId AND n.statusId = s.serverId)
LEFT JOIN TimelineAccountEntity sa ON (n.tuskyAccountId = sa.tuskyAccountId AND s.authorServerId = sa.serverId)
LEFT JOIN NotificationReportEntity r ON (n.tuskyAccountId = r.tuskyAccountId AND n.reportId = r.serverId)
LEFT JOIN TimelineAccountEntity ra ON (n.tuskyAccountId = ra.tuskyAccountId AND r.targetAccountId = ra.serverId)
WHERE n.tuskyAccountId = :tuskyAccountId
ORDER BY LENGTH(n.id) DESC, n.id DESC"""
    )
    abstract fun getNotifications(tuskyAccountId: Long): PagingSource<Int, NotificationDataEntity>

    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND id = :notificationId"""
    )
    abstract suspend fun delete(tuskyAccountId: Long, notificationId: String): Int

    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND
        (LENGTH(id) < LENGTH(:maxId) OR LENGTH(id) == LENGTH(:maxId) AND id <= :maxId)
AND
(LENGTH(id) > LENGTH(:minId) OR LENGTH(id) == LENGTH(:minId) AND id >= :minId)
    """
    )
    abstract suspend fun deleteRange(tuskyAccountId: Long, minId: String, maxId: String): Int

    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId"""
    )
    internal abstract suspend fun removeAllNotifications(tuskyAccountId: Long)

    /**
     * Deletes all NotificationReportEntities for Tusky user with id [tuskyAccountId].
     * Warning: This can violate foreign key constraints if reports are still referenced in the NotificationEntity table.
     */
    @Query(
        """DELETE FROM NotificationReportEntity WHERE tuskyAccountId = :tuskyAccountId"""
    )
    internal abstract suspend fun removeAllReports(tuskyAccountId: Long)

    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId = :statusId"""
    )
    abstract suspend fun deleteAllWithStatus(tuskyAccountId: Long, statusId: String)

    /**
     * Remove all notifications from user with id [userId] unless they are admin notifications.
     */
    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND
            (accountId = :userId OR
            statusId IN (SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId = :userId)
            )
            AND type != "SIGN_UP" AND type != "REPORT"
        """
    )
    abstract suspend fun removeAllByUser(tuskyAccountId: Long, userId: String)

    @Query(
        """DELETE FROM NotificationEntity
            WHERE tuskyAccountId = :tuskyAccountId AND statusId IN (
            SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId in
            ( SELECT serverId FROM TimelineAccountEntity WHERE username LIKE '%@' || :instanceDomain
            AND tuskyAccountId = :tuskyAccountId)
            OR accountId IN ( SELECT serverId FROM TimelineAccountEntity WHERE username LIKE '%@' || :instanceDomain
            AND tuskyAccountId = :tuskyAccountId)
            )"""
    )
    abstract suspend fun deleteAllFromInstance(tuskyAccountId: Long, instanceDomain: String)

    @Query("SELECT id FROM NotificationEntity WHERE tuskyAccountId = :accountId ORDER BY LENGTH(id) DESC, id DESC LIMIT 1")
    abstract suspend fun getTopId(accountId: Long): String?

    @Query("SELECT id FROM NotificationEntity WHERE tuskyAccountId = :accountId AND type IS NULL ORDER BY LENGTH(id) DESC, id DESC LIMIT 1")
    abstract suspend fun getTopPlaceholderId(accountId: Long): String?

    /**
     * Cleans the NotificationEntity table from old entries.
     * @param tuskyAccountId id of the account for which to clean tables
     * @param limit how many timeline items to keep
     */
    @Query(
        """DELETE FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND id NOT IN
        (SELECT id FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT :limit)
    """
    )
    internal abstract suspend fun cleanupNotifications(tuskyAccountId: Long, limit: Int)

    /**
     * Cleans the NotificationReportEntity table from unreferenced entries.
     * @param tuskyAccountId id of the account for which to clean the table
     */
    @Query(
        """DELETE FROM NotificationReportEntity WHERE tuskyAccountId = :tuskyAccountId
        AND serverId NOT IN
        (SELECT reportId FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId and reportId IS NOT NULL)"""
    )
    internal abstract suspend fun cleanupReports(tuskyAccountId: Long)

    /**
     * Returns the id directly above [id], or null if [id] is the id of the top item
     */
    @Query(
        "SELECT id FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND (LENGTH(:id) < LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id < id)) ORDER BY LENGTH(id) ASC, id ASC LIMIT 1"
    )
    abstract suspend fun getIdAbove(tuskyAccountId: Long, id: String): String?

    /**
     * Returns the ID directly below [id], or null if [id] is the ID of the bottom item
     */
    @Query(
        "SELECT id FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND (LENGTH(:id) > LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id > id)) ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getIdBelow(tuskyAccountId: Long, id: String): String?
}
