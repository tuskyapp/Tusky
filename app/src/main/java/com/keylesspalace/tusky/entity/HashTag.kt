package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class HashTag(
        @field:SerializedName("name")
        val name: String
)