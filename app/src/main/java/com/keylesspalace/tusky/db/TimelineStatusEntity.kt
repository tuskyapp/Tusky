package com.keylesspalace.tusky.db

import android.arch.persistence.room.*
import com.keylesspalace.tusky.entity.Status

/**
 * We're trying to play smart here. Server sends us reblogs as two entities one embedded into
 * another (reblogged status is a field inside of "reblog" status). But it's really inefficient from
 * the DB perspective and doesn't matter much for the display/interaction purposes.
 * What if when we store reblog we don't store almost empty "reblog status" but we store
 * *reblogged* status and we embed "reblog status" into reblogged status. This reversed
 * relationship takes much less space and is much faster to fetch (no N+1 type queries or JSON
 * serialization).
 * "Reblog status", if present, is marked by [reblogServerId], [reblogUri] and [reblogAccountId]
 * fields.
 */
@Entity(
        tableName = "timeline_status",
        foreignKeys = ([
            ForeignKey(
                    entity = TimelineAccountEntity::class,
                    parentColumns = ["id"],
                    childColumns = ["authorLocalId"]
            )
        ])
)
@TypeConverters(TootEntity.Converters::class)
data class TimelineStatusEntity(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        val serverId: String,
        val url: String,
        val timelineUserId: Long,
        val authorLocalId: Long,
        val authorServerId: String,
        val instance: String,
        val inReplyToId: String?,
        val inReplyToAccountId: String?,
        val content: String,
        val createdAt: Long,
        val emojis: String?,
        val reblogsCount: Int,
        val favouritesCount: Int,
        val reblogged: Boolean,
        val favourited: Boolean,
        val sensitive: Boolean,
        val spoilerText: String,
        val visibility: Status.Visibility,
        val attachments: String?,
        val mentions: String?,
        val application: String?,
        val reblogServerId: String?,
        val reblogUri: String?,
        val reblogAccountId: Long
)

@Entity(tableName = "timeline_account")
data class TimelineAccountEntity(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        val serverId: String,
        val instance: String,
        val localUsername: String,
        val username: String,
        val displayName: String,
        val url: String,
        val avatar: String
)


class TimelineStatusWithAccount {
    @Embedded
    lateinit var status: TimelineStatusEntity
    @Embedded(prefix = "a_")
    lateinit var account: TimelineAccountEntity
    @Embedded(prefix = "rb_")
    var reblogAccount: TimelineAccountEntity? = null
}