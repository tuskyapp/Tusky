package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class StatusEdit(
    val content: String,
    @Json(name = "spoiler_text") val spoilerText: String,
    val sensitive: Boolean,
    @Json(name = "created_at") val createdAt: Date,
    val account: TimelineAccount,
    val poll: Poll? = null,
    @Json(name = "media_attachments") val mediaAttachments: List<Attachment>,
    val emojis: List<Emoji>
)
