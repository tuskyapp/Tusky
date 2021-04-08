package com.keylesspalace.tusky.db

import androidx.room.*
import com.keylesspalace.tusky.entity.Status

/**
 * We're trying to play smart here. Server sends us reblogs as two entities one embedded into
 * another (reblogged status is a field inside of "reblog" status). But it's really inefficient from
 * the DB perspective and doesn't matter much for the display/interaction purposes.
 * What if when we store reblog we don't store almost empty "reblog status" but we store
 * *reblogged* status and we embed "reblog status" into reblogged status. This reversed
 * relationship takes much less space and is much faster to fetch (no N+1 type queries or JSON
 * serialization).
 * "Reblog status", if present, is marked by [reblogServerId], and [reblogAccountId]
 * fields.
 */
@Entity(
        primaryKeys = ["serverId", "timelineUserId"],
        foreignKeys = ([
            ForeignKey(
                    entity = TimelineAccountEntity::class,
                    parentColumns = ["serverId", "timelineUserId"],
                    childColumns = ["authorServerId", "timelineUserId"]
            )
        ]),
        // Avoiding rescanning status table when accounts table changes. Recommended by Room(c).
        indices = [Index("authorServerId", "timelineUserId")]
)
@TypeConverters(Converters::class)
data class TimelineStatusEntity(
        val serverId: String, // id never flips: we need it for sorting so it's a real id
        val url: String?,
        // our local id for the logged in user in case there are multiple accounts per instance
        val timelineUserId: Long,
        val authorServerId: String?,
        val inReplyToId: String?,
        val inReplyToAccountId: String?,
        val content: String?,
        val createdAt: Long,
        val emojis: String?,
        val reblogsCount: Int,
        val favouritesCount: Int,
        val reblogged: Boolean,
        val bookmarked: Boolean,
        val favourited: Boolean,
        val sensitive: Boolean,
        val spoilerText: String?,
        val visibility: Status.Visibility?,
        val attachments: String?,
        val mentions: String?,
        val application: String?,
        val reblogServerId: String?, // if it has a reblogged status, it's id is stored here
        val reblogAccountId: String?,
        val poll: String?,
        val muted: Boolean?
)

@Entity(
        primaryKeys = ["serverId", "timelineUserId"]
)
data class TimelineAccountEntity(
        val serverId: String,
        val timelineUserId: Long,
        val localUsername: String,
        val username: String,
        val displayName: String,
        val url: String,
        val avatar: String,
        val emojis: String,
        val bot: Boolean
)


class TimelineStatusWithAccount {
    @Embedded
    lateinit var status: TimelineStatusEntity
    @Embedded(prefix = "a_")
    lateinit var account: TimelineAccountEntity
    @Embedded(prefix = "rb_")
    var reblogAccount: TimelineAccountEntity? = null
}