package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import com.keylesspalace.tusky.core.database.model.Attachment
import com.keylesspalace.tusky.core.database.model.Poll
import java.util.Date

data class StatusEdit(
    val content: String,
    @SerializedName("spoiler_text") val spoilerText: String,
    val sensitive: Boolean,
    @SerializedName("created_at") val createdAt: Date,
    val account: TimelineAccount,
    val poll: Poll?,
    @SerializedName("media_attachments") val mediaAttachments: List<Attachment>,
    val emojis: List<Emoji>
)
