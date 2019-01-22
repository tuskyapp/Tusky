@file:JvmName("SpanUtils")
package com.keylesspalace.tusky.util

import android.text.Spannable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import java.util.regex.Pattern

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

private const val WORD_START_PATTERN = "^|\\b"
private const val SCHEME_PATTERN = "\\p{Alpha}[\\p{Alpha}\\d\\.\\-\\+]+"
private const val URL_REGEX = "(?:(${WORD_START_PATTERN})(${SCHEME_PATTERN})://[^\\s]+)"

/**
 * Dump of android.util.Patterns.WEB_URL (with added schemes)
 */
private val STRICT_WEB_URL_PATTERN = Pattern.compile("(((?:(?:(${WORD_START_PATTERN})(${SCHEME_PATTERN}))://(?:(?:[a-zA-Z0-9\\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?)?(?:(([a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]](?:[a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]_\\-]{0,61}[a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]]){0,1}\\.)+(xn\\-\\-[\\w\\-]{0,58}\\w|[a-zA-Z[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]]{2,63})|((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9]))))(?:\\:\\d{1,5})?)([/\\?](?:(?:[a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]];/\\?:@&=#~\\-\\.\\+!\\*'\\(\\),_\\\$])|(?:%[a-fA-F0-9]{2}))*)?(?:\\b|\$|^))")

private val spanClasses = listOf(ForegroundColorSpan::class.java, URLSpan::class.java)
private val finders = mapOf(
    FoundMatchType.URL to PatternFinder(':', URL_REGEX, 0),
    FoundMatchType.TAG to PatternFinder('#', TAG_REGEX, 1),
    FoundMatchType.MENTION to PatternFinder('@', MENTION_REGEX, 1)
)

private enum class FoundMatchType {
    URL,
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
    var foundResult: FindCharsResult? = null
    for (i in fromIndex..string.lastIndex) {
        val c = string[i]
        for ((matchType, finder) in finders) {
            if (finder.searchCharacter == c &&
                    (finder.searchPrefixWidth == 0 ||
                            (i - fromIndex) < finder.searchPrefixWidth ||
                            Character.isWhitespace(string.codePointAt(i - finder.searchPrefixWidth)))) {
                val result = FindCharsResult()
                result.matchType = matchType
                val patternStart = if (finder.searchPrefixWidth == 0) {
                    fromIndex
                } else {
                    Math.max(0, i - finder.searchPrefixWidth)
                }
                result.start = 0
                findEndOfPattern(string.substring(patternStart), result, finder.pattern)
                if (result.start >= 0 && result.end > result.start) {
                    result.start += patternStart
                    result.end += patternStart
                    if (foundResult == null || result.start < foundResult.start) {
                        foundResult = result
                    }
                }
            }
        }

        if (foundResult != null) {
            return foundResult
        }
    }
    return FindCharsResult()
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
            FoundMatchType.URL -> {
                // Preliminary url patterns are fast/permissive, now we'll do full validation
                if (STRICT_WEB_URL_PATTERN.matcher(string.substring(result.start, end)).matches()) {
                    result.end = end
                }
            }
            else -> result.end = end
        }
    }
}

private fun getSpan(matchType: FoundMatchType, string: String, colour: Int, start: Int, end: Int): CharacterStyle {
    return when(matchType) {
        FoundMatchType.URL -> CustomURLSpan(string.substring(start, end))
        else -> ForegroundColorSpan(colour)
    }
}

/** Takes text containing mentions and hashtags and urls and makes them the given colour. */
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
