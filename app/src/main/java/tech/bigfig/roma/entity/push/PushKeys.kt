package tech.bigfig.roma.entity.push

import com.google.gson.annotations.SerializedName

/**
 * Created by pandasoft (joelpyska1@gmail.com) on ${DATE}.
 */

data class PushKeys(
        @field:SerializedName("p256dh") val p256dh: String? = null,
        @field:SerializedName("auth") val auth: String? = null)