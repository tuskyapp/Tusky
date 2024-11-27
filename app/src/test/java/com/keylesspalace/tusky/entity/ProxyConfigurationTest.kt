package com.keylesspalace.tusky.entity

import com.keylesspalace.tusky.settings.ProxyConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyConfigurationTest {
    @Test
    fun `serialized non-int is not valid proxy port`() {
        assertFalse(ProxyConfiguration.isValidProxyPort("should fail"))
        assertFalse(ProxyConfiguration.isValidProxyPort("1.5"))
    }

    @Test
    fun `number outside port range is not valid`() {
        assertFalse(ProxyConfiguration.isValidProxyPort("${ProxyConfiguration.MIN_PROXY_PORT - 1}"))
        assertFalse(ProxyConfiguration.isValidProxyPort("${ProxyConfiguration.MAX_PROXY_PORT + 1}"))
    }

    @Test
    fun `number in port range, inclusive of min and max, is valid`() {
        assertTrue(ProxyConfiguration.isValidProxyPort(ProxyConfiguration.MIN_PROXY_PORT))
        assertTrue(ProxyConfiguration.isValidProxyPort(ProxyConfiguration.MAX_PROXY_PORT))
        assertTrue(ProxyConfiguration.isValidProxyPort((ProxyConfiguration.MIN_PROXY_PORT + ProxyConfiguration.MAX_PROXY_PORT) / 2))
    }

    @Test
    fun `create with invalid port yields null`() {
        assertNull(ProxyConfiguration.create("hostname", ProxyConfiguration.MIN_PROXY_PORT - 1))
    }

    @Test
    fun `create with invalid hostname yields null`() {
        assertNull(ProxyConfiguration.create(".", ProxyConfiguration.MIN_PROXY_PORT))
    }

    @Test
    fun `create with valid hostname and port yields the config object`() {
        assertTrue(ProxyConfiguration.create("hostname", ProxyConfiguration.MIN_PROXY_PORT) is ProxyConfiguration)
    }

    @Test
    fun `unicode hostname allowed`() {
        assertTrue(ProxyConfiguration.create("federação.social", ProxyConfiguration.MIN_PROXY_PORT) is ProxyConfiguration)
    }
}
