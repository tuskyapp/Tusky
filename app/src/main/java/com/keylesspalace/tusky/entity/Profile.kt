package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class Profile(
        @SerializedName("display_name") val displayName: String?,
        val note: String?,
        val avatar: String?,
        val header: String? = null
)

