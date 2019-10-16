package com.keylesspalace.tusky

import android.text.Spannable
import com.keylesspalace.tusky.util.highlightSpans
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class SpanUtilsTest {
    @Test
    fun matchesMixedSpans() {
        val input = "one #one two: @two three : https://thr.ee/meh?foo=bar&wat=@at#hmm four #four five @five"
        val inputSpannable = FakeSpannable(input)
        highlightSpans(inputSpannable, 0xffffff)
        val spans = inputSpannable.spans
        Assert.assertEquals(5, spans.size)
    }

    @Test
    fun doesntMergeAdjacentURLs() {
        val firstURL = "http://first.thing"
        val secondURL = "https://second.thing"
        val inputSpannable = FakeSpannable("$firstURL $secondURL")
        highlightSpans(inputSpannable, 0xffffff)
        val spans = inputSpannable.spans
        Assert.assertEquals(2, spans.size)
        Assert.assertEquals(firstURL.length, spans[0].end - spans[0].start)
        Assert.assertEquals(secondURL.length, spans[1].end - spans[1].start)
    }

    @RunWith(Parameterized::class)
    class MatchingTests(private val thingToHighlight: String) {
        companion object {
            @Parameterized.Parameters(name = "{0}")
            @JvmStatic
            fun data(): Iterable<Any> {
                return listOf(
                        "@mention",
                        "#tag",
                        "https://thr.ee/meh?foo=bar&wat=@at#hmm",
                        "http://thr.ee/meh?foo=bar&wat=@at#hmm"
                )
            }
        }

        @Test
        fun matchesSpanAtStart() {
            val inputSpannable = FakeSpannable(thingToHighlight)
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(1, spans.size)
            Assert.assertEquals(thingToHighlight.length, spans[0].end - spans[0].start)
        }

        @Test
        fun matchesSpanNotAtStart() {
            val inputSpannable = FakeSpannable(" $thingToHighlight")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(1, spans.size)
            Assert.assertEquals(thingToHighlight.length, spans[0].end - spans[0].start)
        }

        @Test
        fun doesNotMatchSpanEmbeddedInText() {
            val inputSpannable = FakeSpannable("aa${thingToHighlight}aa")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertTrue(spans.isEmpty())
        }

        @Test
        fun doesNotMatchSpanEmbeddedInAnotherSpan() {
            val inputSpannable = FakeSpannable("@aa${thingToHighlight}aa")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(1, spans.size)
        }

        @Test
        fun spansDoNotOverlap() {
            val begin = "@begin"
            val end = "#end"
            val inputSpannable = FakeSpannable("$begin $thingToHighlight $end")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(3, spans.size)

            val middleSpan = spans.single { span -> span.start > 0 && span.end < inputSpannable.lastIndex }
            Assert.assertEquals(begin.length + 1, middleSpan.start)
            Assert.assertEquals(inputSpannable.length - end.length - 1, middleSpan.end)
        }
    }

    class FakeSpannable(private val text: String) : Spannable {
        val spans = mutableListOf<BoundedSpan>()

        override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
            spans.add(BoundedSpan(what, start, end))
        }

        override fun <T : Any> getSpans(start: Int, end: Int, type: Class<T>): Array<T> {
            return spans.filter { it.start >= start && it.end <= end && type.isInstance(it)}
                        .map { it.span }
                        .toTypedArray() as Array<T>
        }

        override fun removeSpan(what: Any?) {
            spans.removeIf { span -> span.span == what}
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
            throw NotImplementedError()
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            throw NotImplementedError()
        }

        override fun getSpanStart(tag: Any?): Int {
            throw NotImplementedError()
        }
    }
}