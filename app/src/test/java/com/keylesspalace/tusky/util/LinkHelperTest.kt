package com.keylesspalace.tusky.util

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
