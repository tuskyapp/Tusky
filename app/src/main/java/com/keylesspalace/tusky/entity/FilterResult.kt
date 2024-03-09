package com.keylesspalace.tusky.entity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FilterResult(
    val filter: Filter,
    @Json(name = "keyword_matches") val keywordMatches: List<String> = emptyList(),
    @Json(name = "status_matches") val statusMatches: List<String> = emptyList()
)
