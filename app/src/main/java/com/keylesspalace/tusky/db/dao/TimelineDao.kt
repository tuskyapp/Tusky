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

package com.keylesspalace.tusky.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.keylesspalace.tusky.db.entity.HomeTimelineData
import com.keylesspalace.tusky.db.entity.HomeTimelineEntity

@Dao
abstract class TimelineDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertHomeTimelineItem(item: HomeTimelineEntity): Long

    @Query(
        """
SELECT h.id, s.serverId, s.url, s.tuskyAccountId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt, s.editedAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.repliesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.tags, s.application,
s.content, s.attachments, s.poll, s.card, s.muted, s.expanded, s.contentShowing, s.contentCollapsed, s.pinned, s.language, s.filtered,
a.serverId as 'a_serverId', a.tuskyAccountId as 'a_tuskyAccountId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot',
rb.serverId as 'rb_serverId', rb.tuskyAccountId 'rb_tuskyAccountId',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar',
rb.emojis as 'rb_emojis', rb.bot as 'rb_bot',
h.loading
FROM HomeTimelineEntity h
LEFT JOIN TimelineStatusEntity s ON (h.statusId = s.serverId AND s.tuskyAccountId = :tuskyAccountId)
LEFT JOIN TimelineAccountEntity a ON (s.authorServerId = a.serverId AND a.tuskyAccountId = :tuskyAccountId)
LEFT JOIN TimelineAccountEntity rb ON (h.reblogAccountId = rb.serverId AND rb.tuskyAccountId = :tuskyAccountId)
WHERE h.tuskyAccountId = :tuskyAccountId
ORDER BY LENGTH(h.id) DESC, h.id DESC"""
    )
    abstract fun getHomeTimeline(tuskyAccountId: Long): PagingSource<Int, HomeTimelineData>

    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND
        (LENGTH(id) < LENGTH(:maxId) OR LENGTH(id) == LENGTH(:maxId) AND id <= :maxId)
AND
(LENGTH(id) > LENGTH(:minId) OR LENGTH(id) == LENGTH(:minId) AND id >= :minId)
    """
    )
    abstract suspend fun deleteRange(tuskyAccountId: Long, minId: String, maxId: String): Int

    /**
     * Remove all home timeline items that are statuses or reblogs by the user with id [userId], including reblogs from other people.
     * (e.g. because user was blocked)
     */
    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND
            (statusId IN
            (SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId == :userId)
            OR reblogAccountId == :userId)
        """
    )
    abstract suspend fun removeAllByUser(tuskyAccountId: Long, userId: String)

    /**
     * Remove all home timeline items that are statuses or reblogs by the user with id [userId], but not reblogs from other users.
     * (e.g. because user was unfollowed)
     */
    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND
            ((statusId IN
            (SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId == :userId)
            AND reblogAccountId IS NULL)
            OR reblogAccountId == :userId)
        """
    )
    abstract suspend fun removeStatusesAndReblogsByUser(tuskyAccountId: Long, userId: String)

    @Query("DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun removeAllHomeTimelineItems(tuskyAccountId: Long)

    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND id = :id"""
    )
    abstract suspend fun deleteHomeTimelineItem(tuskyAccountId: Long, id: String)

    /**
     * Deletes all hometimeline items that reference the status with it [statusId]. They can be regular statuses or reblogs.
     */
    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId = :statusId"""
    )
    abstract suspend fun deleteAllWithStatus(tuskyAccountId: Long, statusId: String)

    /**
     * Trims the HomeTimelineEntity table down to [limit] entries by deleting the oldest in case there are more than [limit].
     * @param tuskyAccountId id of the account for which to clean the home timeline
     * @param limit how many timeline items to keep
     */
    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND id NOT IN
        (SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT :limit)
    """
    )
    internal abstract suspend fun cleanupHomeTimeline(tuskyAccountId: Long, limit: Int)

    @Query(
        """DELETE FROM HomeTimelineEntity
WHERE tuskyAccountId = :tuskyAccountId AND statusId IN (
SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId in
( SELECT serverId FROM TimelineAccountEntity WHERE username LIKE '%@' || :instanceDomain
AND tuskyAccountId = :tuskyAccountId
))"""
    )
    abstract suspend fun deleteAllFromInstance(tuskyAccountId: Long, instanceDomain: String)

    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getTopId(tuskyAccountId: Long): String?

    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NULL ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getTopPlaceholderId(tuskyAccountId: Long): String?

    /**
     * Returns the id directly above [id], or null if [id] is the id of the top item
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND (LENGTH(:id) < LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id < id)) ORDER BY LENGTH(id) ASC, id ASC LIMIT 1"
    )
    abstract suspend fun getIdAbove(tuskyAccountId: Long, id: String): String?

    /**
     * Returns the ID directly below [id], or null if [id] is the ID of the bottom item
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND (LENGTH(:id) > LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id > id)) ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getIdBelow(tuskyAccountId: Long, id: String): String?

    @Query("SELECT COUNT(*) FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun getHomeTimelineItemCount(tuskyAccountId: Long): Int

    /** Developer tools: Find N most recent status IDs */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT :count"
    )
    abstract suspend fun getMostRecentNHomeTimelineIds(tuskyAccountId: Long, count: Int): List<String>

    /** Developer tools: Convert a home timeline item to a placeholder */
    @Query("UPDATE HomeTimelineEntity SET statusId = NULL, reblogAccountId = NULL WHERE id = :serverId")
    abstract suspend fun convertHomeTimelineItemToPlaceholder(serverId: String)
}
