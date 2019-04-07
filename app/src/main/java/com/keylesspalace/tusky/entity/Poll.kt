package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import java.util.*

data class Poll(
        val id: String,
        @SerializedName("expires_at") val expiresAt: Date?,
        val expired: Boolean,
        val multiple: Boolean,
        val votes_count: Int,
        val options: List<PollOption>,
        val voted: Boolean?
)

data class PollOption(
        val title: String,
        @SerializedName("votes_count") val votesCount: Int
)