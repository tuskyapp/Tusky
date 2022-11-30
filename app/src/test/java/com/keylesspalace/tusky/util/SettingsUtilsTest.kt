package com.keylesspalace.tusky.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUtilsTest {
    @Test
    fun `non-string port is not valid proxy port`() {
        assertFalse(isValidProxyPort(5))
    }

    @Test
    fun `serialized non-int is not valid proxy port`() {
        assertFalse(isValidProxyPort("should fail"))
        assertFalse(isValidProxyPort("1.5"))
    }

    @Test
    fun `number outside port range is not valid`() {
        assertFalse(isValidProxyPort("${MIN_PROXY_PORT - 1}"))
        assertFalse(isValidProxyPort("${MAX_PROXY_PORT + 1}"))
    }

    @Test
    fun `number in port range, inclusive of min and max, is valid`() {
        assertTrue(isValidProxyPort("$MIN_PROXY_PORT"))
        assertTrue(isValidProxyPort("$MAX_PROXY_PORT"))
        assertTrue(isValidProxyPort("${(MIN_PROXY_PORT + MAX_PROXY_PORT) / 2}"))
    }
}
