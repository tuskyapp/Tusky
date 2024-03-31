package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Poll(
    val id: String,
    @Json(name = "expires_at") val expiresAt: Date? = null,
    val expired: Boolean,
    val multiple: Boolean,
    @Json(name = "votes_count") val votesCount: Int,
    // nullable for compatibility with Pleroma
    @Json(name = "voters_count") val votersCount: Int? = null,
    val options: List<PollOption>,
    val voted: Boolean = false,
    @Json(name = "own_votes") val ownVotes: List<Int> = emptyList()
) {

    fun votedCopy(choices: List<Int>): Poll {
        val newOptions = options.mapIndexed { index, option ->
            if (choices.contains(index)) {
                option.copy(votesCount = (option.votesCount ?: 0) + 1)
            } else {
                option
            }
        }

        return copy(
            options = newOptions,
            votesCount = votesCount + choices.size,
            votersCount = votersCount?.plus(1),
            voted = true
        )
    }

    fun toNewPoll(creationDate: Date) = NewPoll(
        options.map { it.title },
        expiresAt?.let {
            ((it.time - creationDate.time) / 1000).toInt() + 1
        } ?: 3600,
        multiple
    )
}

@JsonClass(generateAdapter = true)
data class PollOption(
    val title: String,
    @Json(name = "votes_count") val votesCount: Int? = null
)
