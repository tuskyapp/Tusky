/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.core.database.model

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Poll(
    val id: String,
    @SerializedName("expires_at") val expiresAt: Date?,
    val expired: Boolean,
    val multiple: Boolean,
    @SerializedName("votes_count") val votesCount: Int,
    @SerializedName("voters_count") val votersCount: Int?, // nullable for compatibility with Pleroma
    val options: List<PollOption>,
    val voted: Boolean,
    @SerializedName("own_votes") val ownVotes: List<Int>?
) {

    fun votedCopy(choices: List<Int>): Poll {
        val newOptions = options.mapIndexed { index, option ->
            if (choices.contains(index)) {
                option.copy(votesCount = option.votesCount + 1)
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

data class PollOption(
    val title: String,
    @SerializedName("votes_count") val votesCount: Int
)
