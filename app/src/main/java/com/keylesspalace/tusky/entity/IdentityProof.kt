package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class IdentityProof(
        val provider: String,
        @SerializedName("provider_username") val username: String,
        @SerializedName("profile_url") val profileUrl: String
)
