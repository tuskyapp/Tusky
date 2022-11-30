package com.keylesspalace.tusky.util

const val MIN_PROXY_PORT = 0
const val MAX_PROXY_PORT = 65535
private val PROXY_RANGE = IntRange(MIN_PROXY_PORT, MAX_PROXY_PORT)

fun isValidProxyPort(value: Any): Boolean = when (value) {
    is String -> if (value == "") true else value.runCatching(String::toInt).map(
        PROXY_RANGE::contains
    ).getOrDefault(false)
    else -> false
}
