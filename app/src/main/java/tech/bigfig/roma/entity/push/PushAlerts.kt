package tech.bigfig.roma.entity.push

import com.google.gson.annotations.SerializedName

/**
 * Created by pandasoft (joelpyska1@gmail.com) on ${DATE}.
 */

data class PushAlerts(
        @field:SerializedName("favourite") val favourite: Boolean = false,
        @field:SerializedName("follow") val follow: Boolean = false,
        @field:SerializedName("mention") val mention: Boolean = false,
        @field:SerializedName("reblog") val reblog: Boolean = false){
    fun isActive():Boolean=favourite||follow||mention||reblog
}