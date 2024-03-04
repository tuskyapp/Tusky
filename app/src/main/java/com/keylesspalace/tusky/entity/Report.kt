package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Report(
    val id: String,
    val category: String,
    val status_ids: List<String> = emptyList(),
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "target_account") val targetAccount: TimelineAccount
)
