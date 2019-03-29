package tech.bigfig.roma.entity.push


import com.google.gson.annotations.SerializedName

data class PushSubscriptionResponse(

	@field:SerializedName("alerts")
	val alerts: PushAlerts? = null,

	@field:SerializedName("endpoint")
	val endpoint: String? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("server_key")
	val serverKey: String? = null
)