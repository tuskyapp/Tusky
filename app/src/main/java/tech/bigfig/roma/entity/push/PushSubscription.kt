package tech.bigfig.roma.entity.push

import com.google.gson.annotations.SerializedName

/**
 * Created by pandasoft (joelpyska1@gmail.com) on ${DATE}.
 */

data class PushSubscription(
        @field:SerializedName("endpoint") val endpoint: String? = null,
        @field:SerializedName("keys") val keys: PushKeys? = null)