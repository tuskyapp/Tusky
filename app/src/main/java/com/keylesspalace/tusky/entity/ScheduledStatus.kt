package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class ScheduledStatus(
        val id: String,
        @SerializedName("scheduled_at") val scheduledAt: String,
        val params: StatusParams,
        @SerializedName("media_attachments") val mediaAttachments: ArrayList<Attachment>
)
