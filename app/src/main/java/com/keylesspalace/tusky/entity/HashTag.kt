package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class HashTag(

        @field:SerializedName("name")
        val name: String,

        @field:SerializedName("url")
        val url: String,

        @field:SerializedName("history")
        val history: List<History?>? = null
)