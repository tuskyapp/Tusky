package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * API type for saving the scroll position of a timeline.
 */
data class Marker(
    @SerializedName("last_read_id")
    val lastReadId: String,
    val version: Int,
    @SerializedName("updated_at")
    val updatedAt: Date
)
