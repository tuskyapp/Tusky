package com.keylesspalace.tusky.settings

import java.net.IDN

class ProxyConfiguration private constructor(
    val hostname: String,
    val port: Int
) {
    companion object {
        fun create(hostname: String, port: Int): ProxyConfiguration? {
            if (isValidHostname(IDN.toASCII(hostname)) && isValidProxyPort(port)) {
                return ProxyConfiguration(hostname, port)
            }
            return null
        }
        fun isValidProxyPort(value: Any): Boolean = when (value) {
            is String -> if (value == "") true else value.runCatching(String::toInt).map(
                PROXY_RANGE::contains
            ).getOrDefault(false)
            is Int -> PROXY_RANGE.contains(value)
            else -> false
        }
        fun isValidHostname(hostname: String): Boolean =
            IP_ADDRESS_REGEX.matches(hostname) || HOSTNAME_REGEX.matches(hostname)
        const val MIN_PROXY_PORT = 1
        const val MAX_PROXY_PORT = 65535
    }
}

private val PROXY_RANGE = IntRange(ProxyConfiguration.MIN_PROXY_PORT, ProxyConfiguration.MAX_PROXY_PORT)
private val IP_ADDRESS_REGEX = Regex("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")
private val HOSTNAME_REGEX = Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$")
