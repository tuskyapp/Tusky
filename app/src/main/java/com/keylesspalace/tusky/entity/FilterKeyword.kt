package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class FilterKeyword(
    val id: String,
    val keyword: String,
    @SerializedName("whole_word") val wholeWord: Boolean,
)
