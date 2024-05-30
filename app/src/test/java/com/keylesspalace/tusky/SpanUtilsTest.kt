package com.keylesspalace.tusky

import android.text.Spannable
import com.keylesspalace.tusky.util.FoundMatchType
import com.keylesspalace.tusky.util.MENTION_PATTERN_STRING
import com.keylesspalace.tusky.util.PatternFinder
import com.keylesspalace.tusky.util.TAG_PATTERN_STRING
import com.keylesspalace.tusky.util.highlightSpans
import com.keylesspalace.tusky.util.twittertext.Regex
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The [Pattern.UNICODE_CHARACTER_CLASS] flag is not supported on Android, on Android it is just always on.
 * Since thesse tests run on a regular Jvm, we need a to set this flag or they would behave differently.
 * */
private val urlPattern = Regex.VALID_URL_PATTERN_STRING.toPattern(Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)
private val tagPattern = TAG_PATTERN_STRING.toPattern(Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)
private val mentionPattern = MENTION_PATTERN_STRING.toPattern(Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)

val finders = listOf(
    PatternFinder("http://", FoundMatchType.HTTP_URL, urlPattern),
    PatternFinder("https://", FoundMatchType.HTTPS_URL, urlPattern),
    PatternFinder("#", FoundMatchType.TAG, tagPattern),
    PatternFinder("@", FoundMatchType.MENTION, mentionPattern)
)

@RunWith(Parameterized::class)
class SpanUtilsTest(
    private val stringToHighlight: String,
    private val highlights: List<Pair<Int, Int>>
) {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data() = listOf(
            arrayOf("@mention", listOf(0 to 8)),
            arrayOf("@mention@server.com", listOf(0 to 19)),
            arrayOf("#tag", listOf(0 to 4)),
            arrayOf("#tåg", listOf(0 to 4)),
            arrayOf("https://thr.ee/meh?foo=bar&wat=@at#hmm", listOf(0 to 38)),
            arrayOf("http://thr.ee/meh?foo=bar&wat=@at#hmm", listOf(0 to 37)),
            arrayOf(
                "one #one two: @two three : https://thr.ee/meh?foo=bar&wat=@at#hmm four #four five @five 6 #six",
                listOf(4 to 8, 14 to 18, 27 to 65, 71 to 76, 82 to 87, 90 to 94)
            ),
            arrayOf("http://first.link https://second.link", listOf(0 to 17, 18 to 37)),
            arrayOf("#test", listOf(0 to 5)),
            arrayOf(" #AfterSpace", listOf(1 to 12)),
            arrayOf("#BeforeSpace ", listOf(0 to 12)),
            arrayOf("@#after_at", listOf(1 to 10)),
            arrayOf("あいうえお#after_hiragana", listOf<Pair<Int, Int>>()),
            arrayOf("##DoubleHash", listOf(1 to 12)),
            arrayOf("###TripleHash", listOf(2 to 13)),
            arrayOf("something#notAHashtag", listOf<Pair<Int, Int>>()),
            arrayOf("test##maybeAHashtag", listOf(5 to 19)),
            arrayOf("testhttp://not.a.url.com", listOf<Pair<Int, Int>>()),
            arrayOf("test@notAMention", listOf<Pair<Int, Int>>()),
            arrayOf("test@notAMention#notAHashtag", listOf<Pair<Int, Int>>()),
            arrayOf("test@notAMention@server.com", listOf<Pair<Int, Int>>()),
            // Mastodon will not highlight this mention, although it would be valid according to their regex
            // arrayOf("@test@notAMention@server.com", listOf<Pair<Int, Int>>()),
            arrayOf("testhttps://not.a.url.com", listOf<Pair<Int, Int>>()),
            arrayOf("#hashtag1", listOf(0 to 9)),
            arrayOf("#1hashtag", listOf(0 to 9)),
            arrayOf("#サイクリング", listOf(0 to 7)),
            arrayOf("#自転車に乗る", listOf(0 to 7)),
            arrayOf("(#test)", listOf(1 to 6)),
            arrayOf(")#test(", listOf<Pair<Int, Int>>()),
            arrayOf("{#test}", listOf(1 to 6)),
            arrayOf("[#test]", listOf(1 to 6)),
            arrayOf("}#test{", listOf(1 to 6)),
            arrayOf("]#test[", listOf(1 to 6)),
            arrayOf("<#test>", listOf(1 to 6)),
            arrayOf(">#test<", listOf(1 to 6)),
            arrayOf("((#Test))", listOf(2 to 7)),
            arrayOf("((##Te)st)", listOf(3 to 6)),
            arrayOf("[@ConnyDuck]", listOf(1 to 11)),
            arrayOf("(@ConnyDuck)", listOf(1 to 11)),
            arrayOf("(@ConnyDuck@chaos.social)", listOf(1 to 24)),
            arrayOf("Test(https://test.xyz/blubb(test)))))))))))", listOf(5 to 33)),
            arrayOf("Test https://test.xyz/blubb(test)))))))))))", listOf(5 to 33)),
            arrayOf("Test https://test.xyz/blubbtest)))))))))))", listOf(5 to 31)),
            arrayOf("#https://test.com", listOf(0 to 6)),
            arrayOf("#https://t", listOf(0 to 6)),
            arrayOf("(https://blubb.com", listOf(1 to 18)),
            arrayOf("https://example.com/path#anchor", listOf(0 to 31)),
            arrayOf("test httpx2345://wrong.protocol.com", listOf<Pair<Int, Int>>()),
            arrayOf("test https://nonexistent.topleveldomain.testtest", listOf<Pair<Int, Int>>()),
            arrayOf("test https://example.com:1234 domain with port", listOf(5 to 29)),
            arrayOf("http://1.1.1.1", listOf<Pair<Int, Int>>()),
            arrayOf("http://foo.bar/?q=Test%20URL-encoded%20stuff", listOf(0 to 44)),
            arrayOf("http://userid:password@example.com", listOf<Pair<Int, Int>>()),
            arrayOf("http://userid@example.com", listOf<Pair<Int, Int>>()),
            arrayOf("http://foo.com/blah_blah_(brackets)_(again)", listOf(0 to 43)),
            arrayOf("test example.com/no/protocol", listOf<Pair<Int, Int>>()),
            arrayOf("protocol only https://", listOf<Pair<Int, Int>>()),
            arrayOf("no tld https://test", listOf<Pair<Int, Int>>()),
            arrayOf("mention in url https://test.com/@test@domain.cat", listOf(15 to 48)),
            arrayOf("#hash_tag", listOf(0 to 9)),
            arrayOf("#hashtag_", listOf(0 to 9)),
            arrayOf("#hashtag_#tag", listOf(0 to 9, 9 to 13)),
            arrayOf("#hash_tag#tag", listOf(0 to 9)),
            arrayOf("_#hashtag", listOf(1 to 9)),
            arrayOf("@@ConnyDuck@chaos.social", listOf(1 to 24)),
            arrayOf("http://https://connyduck.at", listOf(7 to 27)),
            arrayOf("https://https://connyduck.at", listOf(8 to 28)),
            arrayOf("http:// http://connyduck.at", listOf(8 to 27)),
            arrayOf("https:// https://connyduck.at", listOf(9 to 29)),
            arrayOf("https:// #test https://connyduck.at", listOf(9 to 14, 15 to 35)),
            arrayOf("http:// @connyduck http://connyduck.at", listOf(8 to 18, 19 to 38)),
            // emojis count as multiple characters
            arrayOf("😜https://connyduck.at", listOf(2 to 22)),
            arrayOf("😜#tag", listOf(2 to 6)),
            arrayOf("😜@user@mastodon.example", listOf(2 to 24)),
        )
    }

    @Test
    fun testHighlighting() {
        val inputSpannable = FakeSpannable(stringToHighlight)
        inputSpannable.highlightSpans(0xffffff, finders)

        assertEquals(highlights.size, inputSpannable.spans.size)

        inputSpannable.spans.forEachIndexed { index, span ->
            assertEquals(highlights[index].first, span.start)
            assertEquals(highlights[index].second, span.end)
        }
    }
}

class FakeSpannable(private val text: String) : Spannable {
    val spans = mutableListOf<BoundedSpan>()

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        spans.add(BoundedSpan(what, start, end))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSpans(start: Int, end: Int, type: Class<T>): Array<T> {
        return spans.filter { it.start >= start && it.end <= end && type.isInstance(it.span) }
            .map { it.span }
            .toTypedArray() as Array<T>
    }

    override fun removeSpan(what: Any?) {
        spans.removeIf { span -> span.span == what }
    }

    override fun toString(): String {
        return text
    }

    override val length: Int
        get() = text.length

    class BoundedSpan(val span: Any?, val start: Int, val end: Int)

    override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int {
        throw NotImplementedError()
    }

    override fun getSpanEnd(tag: Any?): Int {
        throw NotImplementedError()
    }

    override fun getSpanFlags(tag: Any?): Int {
        throw NotImplementedError()
    }

    override fun get(index: Int): Char {
        return text[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return text.subSequence(startIndex, endIndex)
    }

    override fun getSpanStart(tag: Any?): Int {
        throw NotImplementedError()
    }
}
