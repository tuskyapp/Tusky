package tech.bigfig.roma.entity.push

import com.google.gson.annotations.SerializedName

/**
 * Created by pandasoft (joelpyska1@gmail.com) on ${DATE}.
 */

data class PushData(
        @field:SerializedName("alerts") val alerts: PushAlerts? = null)