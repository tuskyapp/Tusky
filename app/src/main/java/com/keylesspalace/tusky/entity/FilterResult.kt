package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class FilterResult(
    val filter: Filter,
    @SerializedName("keyword_matches") val keywordMatches: List<String>?,

    // `statusMatches` is commented out because it is unclear what the correct type should be.
    // The Mastodon documentation is inconsistent, it's either a String?, or a List<String>?.
    // Getting this wrong crashes during JSON parsing.
    //
    // Since the field isn't used anywhere in Tusky yet it's easier to comment this out so
    // it is not deserialized.
    //
    // This can be restored when https://github.com/mastodon/mastodon/issues/24900 is fixed.
    //    @SerializedName("status_matches") val statusMatches: String?
)
