package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Report(
    val id: String,
    val category: String,
    val status_ids: List<String>?,
    @SerializedName("created_at") val createdAt: Date,
    @SerializedName("target_account") val targetAccount: TimelineAccount,
)
