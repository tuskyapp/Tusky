package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class StatusParams(
        val text: String,
        val sensitive: Boolean,
        val visibility: Status.Visibility,
        @SerializedName("spoiler_text") val spoilerText: String,
        @SerializedName("in_reply_to_id") val inReplyToId: String?
)