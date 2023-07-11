package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class FilterResult(
    val filter: Filter,
    @SerializedName("keyword_matches") val keywordMatches: List<String>?,
    @SerializedName("status_matches") val statusMatches: List<String>?
)
