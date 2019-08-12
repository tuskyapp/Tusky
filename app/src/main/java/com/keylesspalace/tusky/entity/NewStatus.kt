package com.keylesspalace.tusky.entity

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

class NewStatus(
        val status: String,
        @SerializedName("spoiler_text") val warningText: String,
        @SerializedName("in_reply_to_id") val inReplyToId: String?,
        val visibility: String,
        val sensitive: Boolean,
        @SerializedName("media_ids") val mediaIds: List<String>?,
        val poll: NewPoll?
)

@Parcelize
class NewPoll(
        val options: List<String>,
        val expires_in: Int,
        val multiple: Boolean
): Parcelable