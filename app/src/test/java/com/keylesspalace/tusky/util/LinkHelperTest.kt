package com.keylesspalace.tusky.util

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class LinkHelperTest {
    private val listener = object : LinkListener {
        override fun onViewTag(tag: String) { }
        override fun onViewAccount(id: String) { }
        override fun onViewUrl(url: String) { }
    }

    private val mentions = listOf(
        Status.Mention("1", "https://example.com/@user", "user", "user"),
        Status.Mention("2", "https://example.com/@anotherUser", "anotherUser", "anotherUser"),
    )
    private val tags = listOf(
        HashTag("Tusky", "https://example.com/Tags/Tusky"),
        HashTag("mastodev", "https://example.com/Tags/mastodev"),
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenSettingClickableText_mentionUrlsArePreserved() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        var urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            setClickableText(span, builder, mentions, null, listener)
        }

        urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            Assert.assertNotNull(mentions.firstOrNull { it.url == span.url })
        }
    }

    @Test
    fun whenSettingClickableText_nonMentionsAreNotConvertedToMentions() {
        val builder = SpannableStringBuilder()
        val nonMentionUrl = "http://example.com/"
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(nonMentionUrl), 0)
            builder.append(" ")
            builder.append("@${mention.username} ")
        }

        var urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            setClickableText(span, builder, mentions, null, listener)
        }

        urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            Assert.assertEquals(nonMentionUrl, span.url)
        }
    }

    @Test
    fun whenCheckingTags_tagNameIsComparedCaseInsensitively() {
        for (tag in tags) {
            for (mutatedTagName in listOf(tag.name, tag.name.uppercase(), tag.name.lowercase())) {
                val tagName = getTagName("#$mutatedTagName", tags)
                Assert.assertNotNull(tagName)
                Assert.assertNotNull(tags.firstOrNull { it.name == tagName })
            }
        }
    }

    @Test
    fun hashedUrlSpans_withNoMatchingTag_areNotModified() {
        for (tag in tags) {
            Assert.assertNull(getTagName("#not${tag.name}", tags))
        }
    }

    @Test
    fun whenTagsAreNull_tagNameIsGeneratedFromText() {
        for (tag in tags) {
            Assert.assertEquals(tag.name, getTagName("#${tag.name}", null))
        }
    }

    @Test
    fun whenStringIsInvalidUri_emptyStringIsReturnedFromGetDomain() {
        listOf(
            null,
            "foo bar baz",
            "http:/foo.bar",
            "c:/foo/bar",
        ).forEach {
            Assert.assertEquals("", getDomain(it))
        }
    }

    @Test
    fun whenUrlIsValid_correctDomainIsReturned() {
        listOf(
            "example.com",
            "localhost",
            "sub.domain.com",
            "10.45.0.123",
        ).forEach { domain ->
            listOf(
                "https://$domain",
                "https://$domain/",
                "https://$domain/foo/bar",
                "https://$domain/foo/bar.html",
                "https://$domain/foo/bar.html#",
                "https://$domain/foo/bar.html#anchor",
                "https://$domain/foo/bar.html?argument=value",
                "https://$domain/foo/bar.html?argument=value&otherArgument=otherValue",
            ).forEach { url ->
                Assert.assertEquals(domain, getDomain(url))
            }
        }
    }

    @Test
    fun wwwPrefixIsStrippedFromGetDomain() {
        mapOf(
            "https://www.example.com/foo/bar" to "example.com",
            "https://awww.example.com/foo/bar" to "awww.example.com",
            "http://www.localhost" to "localhost",
            "https://wwwexample.com/" to "wwwexample.com",
        ).forEach { (url, domain) ->
            Assert.assertEquals(domain, getDomain(url))
        }
    }

    @Test
    fun hiddenDomainsAreMarkedUp() {
        val displayedContent = "This is a good place to go"
        val maliciousDomain = "malicious.place"
        val maliciousUrl = "https://$maliciousDomain/to/go"
        val content = SpannableStringBuilder()
        content.append(displayedContent, URLSpan(maliciousUrl), 0)
        Assert.assertEquals(context.getString(R.string.url_domain_notifier, displayedContent, maliciousDomain), markupHiddenUrls(context, content, listOf(), listOf(), listener).toString())
    }

    @Test
    fun fraudulentDomainsAreMarkedUp() {
        val displayedContent = "https://tusky.app/"
        val maliciousDomain = "malicious.place"
        val maliciousUrl = "https://$maliciousDomain/to/go"
        val content = SpannableStringBuilder()
        content.append(displayedContent, URLSpan(maliciousUrl), 0)
        Assert.assertEquals(context.getString(R.string.url_domain_notifier, displayedContent, maliciousDomain), markupHiddenUrls(context, content, listOf(), listOf(), listener).toString())
    }

    @Test fun multipleHiddenDomainsAreMarkedUp() {
        val domains = listOf("one.place", "another.place", "athird.place")
        val displayedContent = "link"
        val content = SpannableStringBuilder()
        for (domain in domains) {
            content.append(displayedContent, URLSpan("https://$domain/foo/bar"), 0)
        }

        val markedUpContent = markupHiddenUrls(context, content, listOf(), listOf(), listener)
        for (domain in domains) {
            Assert.assertTrue(markedUpContent.contains(context.getString(R.string.url_domain_notifier, displayedContent, domain)))
        }
    }

    @Test
    fun validMentionsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(context, builder, mentions, tags, listener)
        for (mention in mentions) {
            Assert.assertFalse(markedUpContent.contains("${getDomain(mention.url)})"))
        }
    }

    @Test
    fun invalidMentionsAreMarkedUp() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(context, builder, listOf(), tags, listener)
        for (mention in mentions) {
            Assert.assertTrue(markedUpContent.contains("${getDomain(mention.url)})"))
        }
    }

    @Test
    fun validTagsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (tag in tags) {
            builder.append("#${tag.name}", URLSpan(tag.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(context, builder, mentions, tags, listener)
        for (tag in tags) {
            Assert.assertFalse(markedUpContent.contains("${getDomain(tag.url)})"))
        }
    }

    @Test
    fun invalidTagsAreMarkedUp() {
        val builder = SpannableStringBuilder()
        for (tag in tags) {
            builder.append("#${tag.name}", URLSpan(tag.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(context, builder, mentions, listOf(), listener)
        for (tag in tags) {
            Assert.assertTrue(markedUpContent.contains("${getDomain(tag.url)})"))
        }
    }
}
