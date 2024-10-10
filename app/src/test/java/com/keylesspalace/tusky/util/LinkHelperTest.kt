package com.keylesspalace.tusky.util

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
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
        Status.Mention("2", "https://example.com/@anotherUser", "anotherUser", "anotherUser")
    )
    private val tags = listOf(
        HashTag("Tusky", "https://example.com/Tags/Tusky"),
        HashTag("mastodev", "https://example.com/Tags/mastodev")
    )

    private val textView: TextView
        get() = TextView(InstrumentationRegistry.getInstrumentation().targetContext)

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
            assertNotNull(mentions.firstOrNull { it.url == span.url })
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
            assertEquals(nonMentionUrl, span.url)
        }
    }

    @Test
    fun whenCheckingTags_tagNameIsComparedCaseInsensitively() {
        for (tag in tags) {
            for (mutatedTagName in listOf(tag.name, tag.name.uppercase(), tag.name.lowercase())) {
                val tagName = getTagName("#$mutatedTagName", tags)
                assertNotNull(tagName)
                assertNotNull(tags.firstOrNull { it.name == tagName })
            }
        }
    }

    @Test
    fun whenCheckingTags_tagNameIsNormalized() {
        val mutator = "aeiou".toList().zip("åÉîøÜ".toList()).toMap()
        for (tag in tags) {
            val mutatedTagName = String(tag.name.map { mutator[it] ?: it }.toCharArray())
            val tagName = getTagName("#$mutatedTagName", tags)
            assertNotNull(tagName)
            assertNotNull(tags.firstOrNull { it.name == tagName })
        }
    }

    @Test
    fun hashedUrlSpans_withNoMatchingTag_areNotModified() {
        for (tag in tags) {
            assertNull(getTagName("#not${tag.name}", tags))
        }
    }

    @Test
    fun whenTagsAreNull_tagNameIsGeneratedFromText() {
        for (tag in tags) {
            assertEquals(tag.name, getTagName("#${tag.name}", null))
        }
    }

    @Test
    fun whenStringIsInvalidUri_emptyStringIsReturnedFromGetDomain() {
        listOf(
            null,
            "foo bar baz",
            "http:/foo.bar",
            "c:/foo/bar"
        ).forEach {
            assertEquals("", getDomain(it))
        }
    }

    @Test
    fun whenUrlIsValid_correctDomainIsReturned() {
        listOf(
            "example.com",
            "localhost",
            "sub.domain.com",
            "10.45.0.123"
        ).forEach { domain ->
            listOf(
                "https://$domain",
                "https://$domain/",
                "https://$domain/foo/bar",
                "https://$domain/foo/bar.html",
                "https://$domain/foo/bar.html#",
                "https://$domain/foo/bar.html#anchor",
                "https://$domain/foo/bar.html?argument=value",
                "https://$domain/foo/bar.html?argument=value&otherArgument=otherValue"
            ).forEach { url ->
                assertEquals(domain, getDomain(url))
            }
        }
    }

    @Test
    fun wwwPrefixIsStrippedFromGetDomain() {
        mapOf(
            "https://www.example.com/foo/bar" to "example.com",
            "https://awww.example.com/foo/bar" to "awww.example.com",
            "http://www.localhost" to "localhost",
            "https://wwwexample.com/" to "wwwexample.com"
        ).forEach { (url, domain) ->
            assertEquals(domain, getDomain(url))
        }
    }

    @Test
    fun hiddenDomainsAreMarkedUp() {
        val displayedContent = "This is a good place to go"
        val maliciousDomain = "malicious.place"
        val maliciousUrl = "https://$maliciousDomain/to/go"
        val content = SpannableStringBuilder()
        content.append(displayedContent, URLSpan(maliciousUrl), 0)
        val oldContent = content.toString()
        assertEquals(
            displayedContent + " " + textView.context.getString(R.string.url_domain_notifier, maliciousDomain),
            markupHiddenUrls(textView, content).toString()
        )
        assertEquals(oldContent, content.toString())
    }

    @Test
    fun fraudulentDomainsAreMarkedUp() {
        val displayedContent = "https://tusky.app/"
        val maliciousDomain = "malicious.place"
        val maliciousUrl = "https://$maliciousDomain/to/go"
        val content = SpannableStringBuilder()
        content.append(displayedContent, URLSpan(maliciousUrl), 0)
        assertEquals(
            displayedContent + " " + textView.context.getString(R.string.url_domain_notifier, maliciousDomain),
            markupHiddenUrls(textView, content).toString()
        )
    }

    @Test
    fun multipleHiddenDomainsAreMarkedUp() {
        val domains = listOf("one.place", "another.place", "athird.place")
        val displayedContent = "link"
        val content = SpannableStringBuilder()
        for (domain in domains) {
            content.append(displayedContent, URLSpan("https://$domain/foo/bar"), 0)
        }

        val markedUpContent = markupHiddenUrls(textView, content)
        for (domain in domains) {
            assertTrue(markedUpContent.contains(displayedContent + " " + textView.context.getString(R.string.url_domain_notifier, domain)))
        }
    }

    @Test
    fun nonUriTextExactlyMatchingDomainIsNotMarkedUp() {
        val domain = "some.place"
        val content = SpannableStringBuilder()
            .append(domain, URLSpan("https://$domain/"), 0)
            .append(domain, URLSpan("https://$domain"), 0)
            .append(domain, URLSpan("https://www.$domain"), 0)
            .append("www.$domain", URLSpan("https://$domain"), 0)
            .append("www.$domain", URLSpan("https://$domain/"), 0)
            .append("$domain/", URLSpan("https://$domain/"), 0)
            .append("$domain/", URLSpan("https://$domain"), 0)
            .append("$domain/", URLSpan("https://www.$domain"), 0)

        val markedUpContent = markupHiddenUrls(textView, content)
        assertFalse(markedUpContent.contains("🔗"))
    }

    @Test
    fun spanEndsWithUrlIsNotMarkedUp() {
        val content = SpannableStringBuilder()
            .append("Some Place: some.place", URLSpan("https://some.place"), 0)
            .append("Some Place: some.place/", URLSpan("https://some.place/"), 0)
            .append("Some Place - https://some.place", URLSpan("https://some.place"), 0)
            .append("Some Place | https://some.place/", URLSpan("https://some.place/"), 0)
            .append("Some Place https://some.place/path", URLSpan("https://some.place/path"), 0)

        val markedUpContent = markupHiddenUrls(textView, content)
        assertFalse(markedUpContent.contains("🔗"))
    }

    @Test
    fun spanEndsWithFraudulentUrlIsMarkedUp() {
        val content = SpannableStringBuilder()
            .append("Another Place: another.place", URLSpan("https://some.place"), 0)
            .append("Another Place: another.place/", URLSpan("https://some.place/"), 0)
            .append("Another Place - https://another.place", URLSpan("https://some.place"), 0)
            .append("Another Place | https://another.place/", URLSpan("https://some.place/"), 0)
            .append("Another Place https://another.place/path", URLSpan("https://some.place/path"), 0)

        val markedUpContent = markupHiddenUrls(textView, content)
        val asserts = listOf(
            "Another Place: another.place",
            "Another Place: another.place/",
            "Another Place - https://another.place",
            "Another Place | https://another.place/",
            "Another Place https://another.place/path"
        )
        print(markedUpContent)
        asserts.forEach {
            assertTrue(markedUpContent.contains(it + " " + textView.context.getString(R.string.url_domain_notifier, "some.place")))
        }
    }

    @Test
    fun validMentionsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (mention in mentions) {
            assertFalse(markedUpContent.contains("${getDomain(mention.url)})"))
        }
    }

    @Test
    fun invalidMentionsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (mention in mentions) {
            assertFalse(markedUpContent.contains("${getDomain(mention.url)})"))
        }
    }

    @Test
    fun validTagsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (tag in tags) {
            builder.append("#${tag.name}", URLSpan(tag.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (tag in tags) {
            assertFalse(markedUpContent.contains("${getDomain(tag.url)})"))
        }
    }

    @Test
    fun invalidTagsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (tag in tags) {
            builder.append("#${tag.name}", URLSpan(tag.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (tag in tags) {
            assertFalse(markedUpContent.contains("${getDomain(tag.url)})"))
        }
    }

    @RunWith(Parameterized::class)
    class UrlMatchingTests(private val url: String, private val expectedResult: Boolean) {
        companion object {
            @Parameterized.Parameters(name = "match_{0}")
            @JvmStatic
            fun data(): Iterable<Any> {
                return listOf(
                    arrayOf("https://mastodon.foo.bar/@User", true),
                    arrayOf("http://mastodon.foo.bar/@abc123", true),
                    arrayOf("https://mastodon.foo.bar/@user/345667890345678", true),
                    arrayOf("https://mastodon.foo.bar/@user/3", true),
                    arrayOf("https://mastodon.foo.bar/users/User/statuses/43456787654678", true),
                    arrayOf("https://pleroma.foo.bar/users/meh3223", true),
                    arrayOf("https://pleroma.foo.bar/users/meh3223_bruh", true),
                    arrayOf("https://pleroma.foo.bar/users/2345", true),
                    arrayOf("https://pleroma.foo.bar/notice/9", true),
                    arrayOf("https://pleroma.foo.bar/notice/9345678", true),
                    arrayOf("https://pleroma.foo.bar/notice/wat", true),
                    arrayOf("https://pleroma.foo.bar/notice/9qTHT2ANWUdXzENqC0", true),
                    arrayOf("https://pleroma.foo.bar/objects/abcdef-123-abcd-9876543", true),
                    arrayOf("https://misskey.foo.bar/notes/mew", true),
                    arrayOf("https://misskey.foo.bar/notes/1421564653", true),
                    arrayOf("https://misskey.foo.bar/notes/qwer615985ddf", true),
                    arrayOf("https://friendica.foo.bar/profile/user", true),
                    arrayOf("https://friendica.foo.bar/profile/uSeR", true),
                    arrayOf("https://friendica.foo.bar/profile/user_user", true),
                    arrayOf("https://friendica.foo.bar/profile/123", true),
                    arrayOf("https://friendica.foo.bar/display/abcdef-123-abcd-9876543", true),
                    arrayOf("https://google.com/", false),
                    arrayOf("https://mastodon.foo.bar/@User?foo=bar", false),
                    arrayOf("https://mastodon.foo.bar/@User#foo", false),
                    arrayOf("http://mastodon.foo.bar/@", false),
                    arrayOf("http://mastodon.foo.bar/@/345678", false),
                    arrayOf("https://mastodon.foo.bar/@user/345667890345678/", false),
                    arrayOf("https://mastodon.foo.bar/@user/3abce", false),
                    arrayOf("https://pleroma.foo.bar/users/", false),
                    arrayOf("https://pleroma.foo.bar/users/meow/", false),
                    arrayOf("https://pleroma.foo.bar/users/@meow", false),
                    arrayOf("https://pleroma.foo.bar/notices/123456", false),
                    arrayOf("https://pleroma.foo.bar/notice/@neverhappen/", false),
                    arrayOf("https://pleroma.foo.bar/object/abcdef-123-abcd-9876543", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543/", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd_9876543", false),
                    arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd-9876543", false),
                    arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd-9876543/", false),
                    arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd_9876543", false),
                    arrayOf("https://friendica.foo.bar/profile/@mew", false),
                    arrayOf("https://friendica.foo.bar/profile/@mew/", false),
                    arrayOf("https://misskey.foo.bar/notes/@nyan", false),
                    arrayOf("https://misskey.foo.bar/notes/NYAN123", false),
                    arrayOf("https://misskey.foo.bar/notes/meow123/", false),
                    arrayOf("https://pixelfed.social/p/connyduck/391263492998670833", true),
                    arrayOf("https://pixelfed.social/connyduck", true),
                    arrayOf("https://gts.foo.bar/@goblin/statuses/01GH9XANCJ0TA8Y95VE9H3Y0Q2", true),
                    arrayOf("https://gts.foo.bar/@goblin", true),
                    arrayOf("https://foo.microblog.pub/o/5b64045effd24f48a27d7059f6cb38f5", true),
                    arrayOf("https://bookwyrm.foo.bar/user/User", true),
                    arrayOf("https://bookwyrm.foo.bar/user/User/comment/123456", true)
                )
            }
        }

        @Test
        fun test() {
            assertEquals(expectedResult, looksLikeMastodonUrl(url))
        }
    }
}
