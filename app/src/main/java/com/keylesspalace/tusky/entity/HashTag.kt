package com.keylesspalace.tusky.entity

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HashTag(
    val name: String,
    val url: String,
    val following: Boolean? = null
)
