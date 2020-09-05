package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * API type for saving scroll a position in timeline.
 */
data class Marker(
        @SerializedName("last_read_id")
        val lastReadId: String,
        val version: Int,
        @SerializedName("updated_at")
        val updatedAt: Date
)