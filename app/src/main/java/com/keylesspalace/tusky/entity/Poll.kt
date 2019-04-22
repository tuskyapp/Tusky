package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import java.util.*

data class Poll(
        val id: String,
        @SerializedName("expires_at") val expiresAt: Date?,
        val expired: Boolean,
        val multiple: Boolean,
        @SerializedName("votes_count") val votesCount: Int,
        val options: List<PollOption>,
        val voted: Boolean
) {

    fun votedCopy(choices: List<Int>): Poll {
        val newOptions = options.mapIndexed { index, option ->
            if(choices.contains(index)) {
                option.copy(votesCount = option.votesCount + 1)
            } else {
                option
            }
        }

        return copy(options = newOptions, votesCount = votesCount + 1, voted = true)
    }

}

data class PollOption(
        val title: String,
        @SerializedName("votes_count") val votesCount: Int
)