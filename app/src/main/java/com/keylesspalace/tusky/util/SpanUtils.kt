package com.keylesspalace.tusky.util

import android.text.Spannable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.util.Patterns.WEB_URL
import java.util.regex.Pattern

class SpanUtils {
    companion object {
        /**
         * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/tag.rb">
         *     Tag#HASHTAG_RE</a>.
         */
        private const val TAG_REGEX = "(?:^|[^/)\\w])#([\\w_]*[\\p{Alpha}_][\\w_]*)"

        /**
         * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/account.rb">
         *     Account#MENTION_RE</a>
         */
        private const val MENTION_REGEX = "(?:^|[^/[:word:]])@([a-z0-9_]+(?:@[a-z0-9\\.\\-]+[a-z0-9]+)?)"

        private const val HTTP_URL_REGEX = "(?:(^|\\b)http://[^\\s]+)"
        private const val HTTPS_URL_REGEX = "(?:(^|\\b)https://[^\\s]+)"

        private val spanClasses = listOf(ForegroundColorSpan::class.java, URLSpan::class.java)
        private val finders = mapOf(
            FoundMatchType.HTTP_URL to PatternFinder(':', HTTP_URL_REGEX, 5),
            FoundMatchType.HTTPS_URL to PatternFinder(':', HTTPS_URL_REGEX, 6),
            FoundMatchType.TAG to PatternFinder('#', TAG_REGEX, 1),
            FoundMatchType.MENTION to PatternFinder('@', MENTION_REGEX, 1)
        )

        private enum class FoundMatchType {
            HTTP_URL,
            HTTPS_URL,
            TAG,
            MENTION,
        }

        private class FindCharsResult {
            lateinit var matchType: FoundMatchType
            var start: Int = -1
            var end: Int = -1
        }

        private class PatternFinder(val searchCharacter: Char, regex: String, val searchPrefixWidth: Int) {
            val pattern: Pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        }

        private fun <T> clearSpans(text: Spannable, spanClass: Class<T>) {
            for(span in text.getSpans(0, text.length, spanClass)) {
                text.removeSpan(span)
            }
        }

        private fun findPattern(string: String, fromIndex: Int): FindCharsResult {
            val result = FindCharsResult()
            for (i in fromIndex..string.lastIndex) {
                val c = string[i]
                for (matchType in FoundMatchType.values()) {
                    val finder = finders[matchType]
                    if (finder!!.searchCharacter == c
                            && ((i - fromIndex) < finder.searchPrefixWidth ||
                                    Character.isWhitespace(string.codePointAt(i - finder.searchPrefixWidth)))) {
                        result.matchType = matchType
                        result.start = Math.max(0, i - finder.searchPrefixWidth)
                        findEndOfPattern(string, result, finder.pattern)
                        return result
                    }
                }
            }
            return result
        }

        private fun findEndOfPattern(string: String, result: FindCharsResult, pattern: Pattern) {
            val matcher = pattern.matcher(string)
            if (matcher.find(result.start)) {
                // Once we have API level 26+, we can use named captures...
                val end = matcher.end()
                result.start = matcher.start()
                if (Character.isWhitespace(string.codePointAt(result.start))) {
                    ++result.start
                }
                when(result.matchType) {
                    FoundMatchType.HTTP_URL, FoundMatchType.HTTPS_URL -> {
                        // Preliminary url patterns are fast/permissive, now we'll do full validation
                        if (WEB_URL == null || // This doesn't get mocked for tests :-|
                                WEB_URL.matcher(string.substring(result.start, end)).matches()) {
                            result.end = end
                        }
                    }
                    else -> result.end = end
                }
            }
        }

        private fun getSpan(matchType: FoundMatchType, string: String, colour: Int, start: Int, end: Int): CharacterStyle {
            return when(matchType) {
                FoundMatchType.HTTP_URL -> CustomURLSpan(string.substring(start, end))
                FoundMatchType.HTTPS_URL -> CustomURLSpan(string.substring(start, end))
                else -> ForegroundColorSpan(colour)
            }
        }

        /** Takes text containing mentions and hashtags and urls and makes them the given colour. */
        @JvmStatic
        fun highlightSpans(text: Spannable, colour: Int) {
            // Strip all existing colour spans.
            for (spanClass in spanClasses) {
                clearSpans(text, spanClass)
            }

            // Colour the mentions and hashtags.
            val string = text.toString()
            val length = text.length
            var start = 0
            var end = 0
            while (end >= 0 && end < length && start >= 0) {
                // Search for url first because it can contain the other characters
                val found = findPattern(string, end)
                start = found.start
                end = found.end
                if (start >= 0 && end > start) {
                    text.setSpan(getSpan(found.matchType, string, colour, start, end), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                    start += finders[found.matchType]!!.searchPrefixWidth
                }
            }
        }
    }
}