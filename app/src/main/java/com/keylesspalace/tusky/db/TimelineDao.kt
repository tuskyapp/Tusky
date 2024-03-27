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
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.keylesspalace.tusky.entity.Status

@Dao
abstract class TimelineDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertHomeTimelineItem(item: HomeTimelineEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertAccount(timelineAccountEntity: TimelineAccountEntity): Long

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertStatus(timelineStatusEntity: TimelineStatusEntity): Long

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
    abstract fun getStatuses(tuskyAccountId: Long): PagingSource<Int, HomeTimelineData>

    @Transaction
    open suspend fun getStatusWithAccount(accountId: Long, statusId: String): Pair<TimelineStatusEntity, TimelineAccountEntity>? {
        val status = getStatus(accountId, statusId) ?: return null
        val account = getAccount(accountId, status.authorServerId) ?: return null
        return status to account
    }

    @Query(
        """
SELECT * FROM TimelineStatusEntity s
WHERE s.serverId = :statusId
AND s.authorServerId IS NOT NULL
AND s.tuskyAccountId = :tuskyAccountId"""
    )
    abstract suspend fun getStatus(tuskyAccountId: Long, statusId: String): TimelineStatusEntity?

    @Query(
        """
SELECT * FROM TimelineAccountEntity a
WHERE a.serverId = :accountId
AND a.tuskyAccountId = :tuskyAccountId"""
    )
    abstract suspend fun getAccount(tuskyAccountId: Long, accountId: String): TimelineAccountEntity?

    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND
        (LENGTH(id) < LENGTH(:maxId) OR LENGTH(id) == LENGTH(:maxId) AND id <= :maxId)
AND
(LENGTH(id) > LENGTH(:minId) OR LENGTH(id) == LENGTH(:minId) AND id >= :minId)
    """
    )
    abstract suspend fun deleteRange(tuskyAccountId: Long, minId: String, maxId: String): Int

    suspend fun update(tuskyAccountId: Long, status: Status, gson: Gson) {
        update(
            tuskyAccountId = tuskyAccountId,
            statusId = status.id,
            content = status.content,
            editedAt = status.editedAt?.time,
            emojis = gson.toJson(status.emojis),
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            repliesCount = status.repliesCount,
            reblogged = status.reblogged,
            bookmarked = status.bookmarked,
            favourited = status.favourited,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            visibility = status.visibility,
            attachments = gson.toJson(status.attachments),
            mentions = gson.toJson(status.mentions),
            tags = gson.toJson(status.tags),
            poll = gson.toJson(status.poll),
            muted = status.muted,
            pinned = status.pinned ?: false,
            card = gson.toJson(status.card),
            language = status.language
        )
    }

    @Query(
        """UPDATE TimelineStatusEntity
           SET content = :content,
           editedAt = :editedAt,
           emojis = :emojis,
           reblogsCount = :reblogsCount,
           favouritesCount = :favouritesCount,
           repliesCount = :repliesCount,
           reblogged = :reblogged,
           bookmarked = :bookmarked,
           favourited = :favourited,
           sensitive = :sensitive,
           spoilerText = :spoilerText,
           visibility = :visibility,
           attachments = :attachments,
           mentions = :mentions,
           tags = :tags,
           poll = :poll,
           muted = :muted,
           pinned = :pinned,
           card = :card,
           language = :language
           WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    @TypeConverters(Converters::class)
    abstract suspend fun update(
        tuskyAccountId: Long,
        statusId: String,
        content: String?,
        editedAt: Long?,
        emojis: String?,
        reblogsCount: Int,
        favouritesCount: Int,
        repliesCount: Int,
        reblogged: Boolean,
        bookmarked: Boolean,
        favourited: Boolean,
        sensitive: Boolean,
        spoilerText: String,
        visibility: Status.Visibility,
        attachments: String?,
        mentions: String?,
        tags: String?,
        poll: String?,
        muted: Boolean?,
        pinned: Boolean,
        card: String?,
        language: String?
    )

    @Query(
        """UPDATE TimelineStatusEntity SET bookmarked = :bookmarked
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setBookmarked(tuskyAccountId: Long, statusId: String, bookmarked: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET reblogged = :reblogged
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setReblogged(tuskyAccountId: Long, statusId: String, reblogged: Boolean)

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId = :userId"""
    )
    abstract suspend fun removeAllByUser(tuskyAccountId: Long, userId: String)

    /**
     * Removes everything in the TimelineStatusEntity and TimelineAccountEntity tables for one user account
     * @param accountId id of the account for which to clean tables
     */
    suspend fun removeAll(accountId: Long) {
        removeAllStatuses(accountId)
        removeAllAccounts(accountId)
    }

    @Query("DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun removeAllStatuses(tuskyAccountId: Long)

    @Query("DELETE FROM TimelineAccountEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun removeAllAccounts(tuskyAccountId: Long)

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId
AND serverId = :statusId"""
    )
    abstract suspend fun delete(tuskyAccountId: Long, statusId: String)

    /**
     * Cleans the TimelineStatusEntity and TimelineAccountEntity tables from old entries.
     * @param tuskyAccountId id of the account for which to clean tables
     * @param limit how many timeline items to keep
     */
    suspend fun cleanup(tuskyAccountId: Long, limit: Int) {
        cleanupHomeTimeline(tuskyAccountId, limit)
        cleanupStatuses(tuskyAccountId)
        cleanupAccounts(tuskyAccountId)
    }

    /**
     * Cleans the TimelineStatusEntity and TimelineAccountEntity tables from old entries.
     * @param tuskyAccountId id of the account for which to clean tables
     * @param limit how many timeline items to keep
     */
    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND id NOT IN
        (SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT :limit)
    """
    )
    protected abstract suspend fun cleanupHomeTimeline(tuskyAccountId: Long, limit: Int)

    /**
     * Cleans the TimelineStatusEntity table from unreferenced status entries.
     * @param tuskyAccountId id of the account for which to clean statuses
     */
    @Query(
        """DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId
        AND serverId NOT IN
        (SELECT statusId FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NOT NULL)
        AND serverId NOT IN
        (SELECT statusId FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NOT NULL)"""
    )
    protected abstract suspend fun cleanupStatuses(tuskyAccountId: Long)

    /**
     * Cleans the TimelineAccountEntity table from accounts that are no longer referenced by either TimelineStatusEntity, HomeTimelineEntity or NotificationEntity
     * @param tuskyAccountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """DELETE FROM TimelineAccountEntity WHERE tuskyAccountId = :tuskyAccountId
        AND serverId NOT IN
        (SELECT authorServerId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId)
        AND serverId NOT IN
        (SELECT reblogAccountId FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND reblogAccountId IS NOT NULL)
        AND serverId NOT IN
        (SELECT accountId FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND accountId IS NOT NULL)"""
    )
    protected abstract suspend fun cleanupAccounts(tuskyAccountId: Long)

    @Query(
        """UPDATE TimelineStatusEntity SET poll = :poll
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setVoted(tuskyAccountId: Long, statusId: String, poll: String)

    @Query(
        """UPDATE TimelineStatusEntity SET expanded = :expanded
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setExpanded(tuskyAccountId: Long, statusId: String, expanded: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET contentShowing = :contentShowing
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setContentShowing(
        tuskyAccountId: Long,
        statusId: String,
        contentShowing: Boolean
    )

    @Query(
        """UPDATE TimelineStatusEntity SET contentCollapsed = :contentCollapsed
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setContentCollapsed(
        tuskyAccountId: Long,
        statusId: String,
        contentCollapsed: Boolean
    )

    @Query(
        """UPDATE TimelineStatusEntity SET pinned = :pinned
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setPinned(tuskyAccountId: Long, statusId: String, pinned: Boolean)

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
        "UPDATE TimelineStatusEntity SET filtered = NULL WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"
    )
    abstract suspend fun clearWarning(tuskyAccountId: Long, statusId: String): Int

    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getTopId(tuskyAccountId: Long): String?

    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NULL ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getTopPlaceholderId(tuskyAccountId: Long): String?

    /**
     * Returns the id directly above [serverId], or null if [serverId] is the id of the top status
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND (LENGTH(:serverId) < LENGTH(id) OR (LENGTH(:serverId) = LENGTH(id) AND :serverId < id)) ORDER BY LENGTH(id) ASC, id ASC LIMIT 1"
    )
    abstract suspend fun getIdAbove(tuskyAccountId: Long, serverId: String): String?

    /**
     * Returns the ID directly below [serverId], or null if [serverId] is the ID of the bottom
     * status
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND (LENGTH(:serverId) > LENGTH(id) OR (LENGTH(:serverId) = LENGTH(id) AND :serverId > id)) ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getIdBelow(tuskyAccountId: Long, serverId: String): String?

    /**
     * Returns the id of the next placeholder after [serverId]
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NULL AND (LENGTH(:serverId) > LENGTH(id) OR (LENGTH(:serverId) = LENGTH(id) AND :serverId > id)) ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getNextPlaceholderIdAfter(tuskyAccountId: Long, serverId: String): String?

    @Query("SELECT COUNT(*) FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun getHomeTimelineItemCount(tuskyAccountId: Long): Int

    /** Developer tools: Find N most recent status IDs */
    @Query(
        "SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT :count"
    )
    abstract suspend fun getMostRecentNStatusIds(tuskyAccountId: Long, count: Int): List<String>

    /** Developer tools: Convert a home timeline item to a placeholder */
    @Query("UPDATE HomeTimelineEntity SET statusId = NULL, reblogAccountId = NULL WHERE id = :serverId")
    abstract suspend fun convertStatusToPlaceholder(serverId: String)
}
