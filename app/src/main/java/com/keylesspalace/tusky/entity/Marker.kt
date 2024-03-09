package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * API type for saving the scroll position of a timeline.
 */
@JsonClass(generateAdapter = true)
data class Marker(
    @Json(name = "last_read_id")
    val lastReadId: String,
    val version: Int,
    @Json(name = "updated_at")
    val updatedAt: Date
)
