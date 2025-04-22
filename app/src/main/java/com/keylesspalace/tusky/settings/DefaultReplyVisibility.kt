package com.keylesspalace.tusky.settings

import com.keylesspalace.tusky.entity.Status

enum class DefaultReplyVisibility(val int: Int) {
    MATCH_DEFAULT_POST_VISIBILITY(0),
    PUBLIC(1),
    UNLISTED(2),
    PRIVATE(3),
    DIRECT(4);

    val stringValue: String
        get() = when (this) {
            MATCH_DEFAULT_POST_VISIBILITY -> "match_default_post_visibility"
            PUBLIC -> "public"
            UNLISTED -> "unlisted"
            PRIVATE -> "private"
            DIRECT -> "direct"
        }

    fun toVisibilityOr(default: Status.Visibility): Status.Visibility = when (this) {
        PUBLIC -> Status.Visibility.PUBLIC
        UNLISTED -> Status.Visibility.UNLISTED
        PRIVATE -> Status.Visibility.PRIVATE
        DIRECT -> Status.Visibility.DIRECT
        else -> default
    }

    companion object {
        fun fromInt(int: Int): DefaultReplyVisibility = when (int) {
            4 -> DIRECT
            3 -> PRIVATE
            2 -> UNLISTED
            1 -> PUBLIC
            else -> MATCH_DEFAULT_POST_VISIBILITY
        }

        fun fromStringValue(s: String): DefaultReplyVisibility = when (s) {
            "public" -> PUBLIC
            "unlisted" -> UNLISTED
            "private" -> PRIVATE
            "direct" -> DIRECT
            else -> MATCH_DEFAULT_POST_VISIBILITY
        }
    }
}
