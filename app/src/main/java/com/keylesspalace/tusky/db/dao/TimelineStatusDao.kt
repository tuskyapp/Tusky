/* Copyright 2024 Tusky Contributors
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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.db.entity.TimelineAccountEntity
import com.keylesspalace.tusky.db.entity.TimelineStatusEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PreviewCard
import com.keylesspalace.tusky.entity.Status
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter

@Dao
abstract class TimelineStatusDao(
    private val db: AppDatabase
) {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insert(timelineStatusEntity: TimelineStatusEntity): Long

    @Transaction
    open suspend fun getStatusWithAccount(tuskyAccountId: Long, statusId: String): Pair<TimelineStatusEntity, TimelineAccountEntity>? {
        val status = getStatus(tuskyAccountId, statusId) ?: return null
        val account = db.timelineAccountDao().getAccount(tuskyAccountId, status.authorServerId) ?: return null
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

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun update(tuskyAccountId: Long, status: Status, moshi: Moshi) {
        update(
            tuskyAccountId = tuskyAccountId,
            statusId = status.id,
            content = status.content,
            editedAt = status.editedAt?.time,
            emojis = moshi.adapter<List<Emoji>?>().toJson(status.emojis),
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            repliesCount = status.repliesCount,
            reblogged = status.reblogged,
            bookmarked = status.bookmarked,
            favourited = status.favourited,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            visibility = status.visibility,
            attachments = moshi.adapter<List<Attachment>?>().toJson(status.attachments),
            mentions = moshi.adapter<List<Status.Mention>?>().toJson(status.mentions),
            tags = moshi.adapter<List<HashTag>?>().toJson(status.tags),
            poll = moshi.adapter<Poll?>().toJson(status.poll),
            muted = status.muted,
            pinned = status.pinned,
            card = moshi.adapter<PreviewCard?>().toJson(status.card),
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

    @Query("DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun removeAllStatuses(tuskyAccountId: Long)

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
    internal abstract suspend fun cleanupStatuses(tuskyAccountId: Long)

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
        "UPDATE TimelineStatusEntity SET filtered = '[]' WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"
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

    /**
     * Returns the id of the next placeholder after [id], or null if there is no placeholder.
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NULL AND (LENGTH(:id) > LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id > id)) ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getNextPlaceholderIdAfter(tuskyAccountId: Long, id: String): String?

    @Query("SELECT COUNT(*) FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun getHomeTimelineItemCount(tuskyAccountId: Long): Int

    /** Developer tools: Find N most recent status IDs */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId ORDER BY LENGTH(id) DESC, id DESC LIMIT :count"
    )
    abstract suspend fun getMostRecentNStatusIds(tuskyAccountId: Long, count: Int): List<String>

    /** Developer tools: Convert a home timeline item to a placeholder */
    @Query("UPDATE HomeTimelineEntity SET statusId = NULL, reblogAccountId = NULL WHERE id = :serverId")
    abstract suspend fun convertStatusToPlaceholder(serverId: String)
}
