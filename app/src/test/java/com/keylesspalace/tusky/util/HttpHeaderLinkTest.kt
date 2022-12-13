package com.keylesspalace.tusky.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class HttpHeaderLinkTest {
    data class TestData(val name: String, val input: String, val want: List<HttpHeaderLink>)

    @Test
    fun shouldParseValidLinks() {
        // Constructor is private, so call through reflection
        val constructor = HttpHeaderLink::class.java.getDeclaredConstructor(String::class.java)
        constructor.isAccessible = true

        val testData = arrayOf(
            // Examples from https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Link
            TestData(
                "Single URL",
                "<https://example.com>",
                listOf(constructor.newInstance("https://example.com"))
            ),
            TestData(
                "Single URL with parameters",
                "<https://example.com>; rel=\"preconnect\"",
                listOf(constructor.newInstance("https://example.com"))
            ),
            TestData(
                "Single encoded URL with parameters",
                "<https://example.com/%E8%8B%97%E6%9D%A1>; rel=\"preconnect\"",
                listOf(constructor.newInstance("https://example.com/%E8%8B%97%E6%9D%A1"))
            ),
            TestData(
                "Multiple URLs, separated by commas",
                "<https://one.example.com>; rel=\"preconnect\", <https://two.example.com>; rel=\"preconnect\", <https://three.example.com>; rel=\"preconnect\"",
                listOf(
                    constructor.newInstance("https://one.example.com"),
                    constructor.newInstance("https://two.example.com"),
                    constructor.newInstance("https://three.example.com")
                )
            ),
            // Examples from https://httpwg.org/specs/rfc8288.html#rfc.section.3.5
            TestData(
                "Single URL, multiple parameters",
                "<http://example.com/TheBook/chapter2>; rel=\"previous\"; title=\"previous chapter\"",
                listOf(constructor.newInstance("http://example.com/TheBook/chapter2"))
            ),
            TestData(
                "Root resource",
                "</>; rel=\"http://example.net/foo\"",
                listOf(constructor.newInstance("/"))
            ),
            TestData(
                "Terms and anchor",
                "</terms>; rel=\"copyright\"; anchor=\"#foo\"",
                listOf(constructor.newInstance("/terms"))
            ),
            TestData(
                "Multiple URLs with parameter encoding",
                "</TheBook/chapter2>; rel=\"previous\"; title*=UTF-8'de'letztes%20Kapitel, </TheBook/chapter4>; rel=\"next\"; title*=UTF-8'de'n%c3%a4chstes%20Kapitel",
                listOf(
                    constructor.newInstance("/TheBook/chapter2"),
                    constructor.newInstance("/TheBook/chapter4")
                )
            )
        )

        // Verify that the URLs are parsed correctly
        for (test in testData) {
            val links = HttpHeaderLink.parse(test.input)
            assertEquals("${test.name}: Same size", links.size, test.want.size)
            for (i in links.indices) {
                assertEquals(test.name, test.want[i].uri, links[i].uri)
            }
        }
    }
}
