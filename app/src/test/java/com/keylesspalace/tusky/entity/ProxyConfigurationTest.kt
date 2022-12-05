package com.keylesspalace.tusky.entity

import com.keylesspalace.tusky.entity.ProxyConfiguration.Companion.MAX_PROXY_PORT
import com.keylesspalace.tusky.entity.ProxyConfiguration.Companion.MIN_PROXY_PORT
import com.keylesspalace.tusky.entity.ProxyConfiguration.Companion.isValidProxyPort
import org.junit.Assert
import org.junit.Test

class ProxyConfigurationTest {
    @Test
    fun `serialized non-int is not valid proxy port`() {
        Assert.assertFalse(isValidProxyPort("should fail"))
        Assert.assertFalse(isValidProxyPort("1.5"))
    }

    @Test
    fun `number outside port range is not valid`() {
        Assert.assertFalse(isValidProxyPort("${MIN_PROXY_PORT - 1}"))
        Assert.assertFalse(isValidProxyPort("${MAX_PROXY_PORT + 1}"))
    }

    @Test
    fun `number in port range, inclusive of min and max, is valid`() {
        Assert.assertTrue(isValidProxyPort(MIN_PROXY_PORT))
        Assert.assertTrue(isValidProxyPort(MAX_PROXY_PORT))
        Assert.assertTrue(isValidProxyPort((MIN_PROXY_PORT + MAX_PROXY_PORT) / 2))
    }

    @Test
    fun `create with invalid port yields null`() {
        Assert.assertNull(ProxyConfiguration.create("hostname", MIN_PROXY_PORT - 1))
    }

    @Test
    fun `create with invalid hostname yields null`() {
        Assert.assertNull(ProxyConfiguration.create(".", MIN_PROXY_PORT))
    }

    @Test
    fun `create with valid hostname and port yields the config object`() {
        Assert.assertTrue(ProxyConfiguration.create("hostname", MIN_PROXY_PORT) is ProxyConfiguration)
    }

    @Test
    fun `unicode hostname allowed`() {
        Assert.assertTrue(ProxyConfiguration.create("federação.social", MIN_PROXY_PORT) is ProxyConfiguration)
    }
}
