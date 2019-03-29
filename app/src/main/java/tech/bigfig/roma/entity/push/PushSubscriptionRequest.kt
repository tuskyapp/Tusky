package tech.bigfig.roma.entity.push

import com.google.gson.annotations.SerializedName

/**
 * Created by pandasoft (joelpyska1@gmail.com) on ${DATE}.
 */

data class PushSubscriptionRequest(
        @field:SerializedName("subscription") val subscription: PushSubscription? = null,
        @field:SerializedName("data") val data: PushData? = null)