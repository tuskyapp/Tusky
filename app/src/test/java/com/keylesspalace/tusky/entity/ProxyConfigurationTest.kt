package com.keylesspalace.tusky.entity

import com.keylesspalace.tusky.settings.ProxyConfiguration
import org.junit.Assert
import org.junit.Test

class ProxyConfigurationTest {
    @Test
    fun `serialized non-int is not valid proxy port`() {
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("should fail"))
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("1.5"))
    }

    @Test
    fun `number outside port range is not valid`() {
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("${ProxyConfiguration.MIN_PROXY_PORT - 1}"))
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("${ProxyConfiguration.MAX_PROXY_PORT + 1}"))
    }

    @Test
    fun `number in port range, inclusive of min and max, is valid`() {
        Assert.assertTrue(ProxyConfiguration.isValidProxyPort(ProxyConfiguration.MIN_PROXY_PORT))
        Assert.assertTrue(ProxyConfiguration.isValidProxyPort(ProxyConfiguration.MAX_PROXY_PORT))
        Assert.assertTrue(ProxyConfiguration.isValidProxyPort((ProxyConfiguration.MIN_PROXY_PORT + ProxyConfiguration.MAX_PROXY_PORT) / 2))
    }

    @Test
    fun `create with invalid port yields null`() {
        Assert.assertNull(ProxyConfiguration.create("hostname", ProxyConfiguration.MIN_PROXY_PORT - 1))
    }

    @Test
    fun `create with invalid hostname yields null`() {
        Assert.assertNull(ProxyConfiguration.create(".", ProxyConfiguration.MIN_PROXY_PORT))
    }

    @Test
    fun `create with valid hostname and port yields the config object`() {
        Assert.assertTrue(ProxyConfiguration.create("hostname", ProxyConfiguration.MIN_PROXY_PORT) is ProxyConfiguration)
    }

    @Test
    fun `unicode hostname allowed`() {
        Assert.assertTrue(ProxyConfiguration.create("federação.social", ProxyConfiguration.MIN_PROXY_PORT) is ProxyConfiguration)
    }
}
