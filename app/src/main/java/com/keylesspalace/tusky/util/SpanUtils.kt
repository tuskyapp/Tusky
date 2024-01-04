package com.keylesspalace.tusky.util

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.DynamicDrawableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import java.util.regex.Pattern
import kotlin.math.max

/**
 * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/tag.rb">
 *     Tag#HASHTAG_RE</a>.
 */
private const val HASHTAG_SEPARATORS = "_\\u00B7\\u200c"
private const val UNICODE_WORD = "\\p{L}\\p{Mn}\\p{Nd}\\p{Nl}\\p{Pc}" // Ugh, java ( https://stackoverflow.com/questions/4304928/unicode-equivalents-for-w-and-b-in-java-regular-expressions )
private const val TAG_REGEX = "(?:^|[^/)\\w])#(([${UNICODE_WORD}_][$UNICODE_WORD$HASHTAG_SEPARATORS]*[\\p{Alpha}$HASHTAG_SEPARATORS][$UNICODE_WORD$HASHTAG_SEPARATORS]*[${UNICODE_WORD}_])|([${UNICODE_WORD}_]*[\\p{Alpha}][${UNICODE_WORD}_]*))"

/**
 * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/account.rb">
 *     Account#MENTION_RE</a>
 */
private const val USERNAME_REGEX = "[\\w]+([\\w\\.-]+[\\w]+)?"
private const val MENTION_REGEX = "(?<=^|[^\\/$UNICODE_WORD])@(($USERNAME_REGEX)(?:@[$UNICODE_WORD\\.\\-]+[$UNICODE_WORD]+)?)"

private const val HTTP_URL_REGEX = "(?:(^|\\b)http://[^\\s]+)"
private const val HTTPS_URL_REGEX = "(?:(^|\\b)https://[^\\s]+)"

/**
 * Dump of android.util.Patterns.WEB_URL
 */
private val STRICT_WEB_URL_PATTERN = Pattern.compile("(((?:(?i:http|https|rtsp)://(?:(?:[a-zA-Z0-9\\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?)?(?:(([a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]](?:[a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]_\\-]{0,61}[a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]]){0,1}\\.)+(xn\\-\\-[\\w\\-]{0,58}\\w|[a-zA-Z[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]]]{2,63})|((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9]))))(?:\\:\\d{1,5})?)([/\\?](?:(?:[a-zA-Z0-9[ -\uD7FF豈-\uFDCFﷰ-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^ [ - ]\u2028\u2029 　]];/\\?:@&=#~\\-\\.\\+!\\*'\\(\\),_\\\$])|(?:%[a-fA-F0-9]{2}))*)?(?:\\b|\$|^))")

private val spanClasses = listOf(ForegroundColorSpan::class.java, URLSpan::class.java)
private val finders = mapOf(
    FoundMatchType.HTTP_URL to PatternFinder(':', HTTP_URL_REGEX, 5, Character::isWhitespace),
    FoundMatchType.HTTPS_URL to PatternFinder(':', HTTPS_URL_REGEX, 6, Character::isWhitespace),
    FoundMatchType.TAG to PatternFinder('#', TAG_REGEX, 1, ::isValidForTagPrefix),
    FoundMatchType.MENTION to PatternFinder('@', MENTION_REGEX, 1, Character::isWhitespace) // TODO: We also need a proper validator for mentions
)

private enum class FoundMatchType {
    HTTP_URL,
    HTTPS_URL,
    TAG,
    MENTION
}

private class FindCharsResult {
    lateinit var matchType: FoundMatchType
    var start: Int = -1
    var end: Int = -1
}

private class PatternFinder(
    val searchCharacter: Char,
    regex: String,
    val searchPrefixWidth: Int,
    val prefixValidator: (Int) -> Boolean
) {
    val pattern: Pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
}

/**
 * Takes text containing mentions and hashtags and urls and makes them the given colour.
 */
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
    while (end in 0 until length && start >= 0) {
        // Search for url first because it can contain the other characters
        val found = findPattern(string, end)
        start = found.start
        end = found.end
        if (start in 0 until end) {
            text.setSpan(getSpan(found.matchType, string, colour, start, end), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            start += finders[found.matchType]!!.searchPrefixWidth
        }
    }
}

/**
 * Replaces text of the form [iconics name] with their spanned counterparts (ImageSpan).
 */
fun addDrawables(text: CharSequence, color: Int, size: Int, context: Context): Spannable {
    val builder = SpannableStringBuilder(text)

    val pattern = Pattern.compile("\\[iconics ([0-9a-z_]+)\\]")
    val matcher = pattern.matcher(builder)
    while (matcher.find()) {
        val resourceName = matcher.group(1)
            ?: continue

        val drawable = IconicsDrawable(context, GoogleMaterial.getIcon(resourceName))
        drawable.setBounds(0, 0, size, size)
        drawable.setTint(color)

        builder.setSpan(ImageSpan(drawable, DynamicDrawableSpan.ALIGN_BASELINE), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    return builder
}

private fun <T> clearSpans(text: Spannable, spanClass: Class<T>) {
    for (span in text.getSpans(0, text.length, spanClass)) {
        text.removeSpan(span)
    }
}

private fun findPattern(string: String, fromIndex: Int): FindCharsResult {
    val result = FindCharsResult()
    for (i in fromIndex..string.lastIndex) {
        val c = string[i]
        for (matchType in FoundMatchType.entries) {
            val finder = finders[matchType]
            if (finder!!.searchCharacter == c &&
                (
                    (i - fromIndex) < finder.searchPrefixWidth ||
                        finder.prefixValidator(string.codePointAt(i - finder.searchPrefixWidth))
                    )
            ) {
                result.matchType = matchType
                result.start = max(0, i - finder.searchPrefixWidth)
                findEndOfPattern(string, result, finder.pattern)
                if (result.start + finder.searchPrefixWidth <= i + 1 && // The found result is actually triggered by the correct search character
                    result.end >= result.start
                ) { // ...and we actually found a valid result
                    return result
                }
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
        when (result.matchType) {
            FoundMatchType.TAG -> {
                if (isValidForTagPrefix(string.codePointAt(result.start))) {
                    if (string[result.start] != '#' ||
                        (string[result.start] == '#' && string[result.start + 1] == '#')
                    ) {
                        ++result.start
                    }
                }
            }
            else -> {
                if (Character.isWhitespace(string.codePointAt(result.start))) {
                    ++result.start
                }
            }
        }
        when (result.matchType) {
            FoundMatchType.HTTP_URL, FoundMatchType.HTTPS_URL -> {
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
    return when (matchType) {
        FoundMatchType.HTTP_URL -> NoUnderlineURLSpan(string.substring(start, end))
        FoundMatchType.HTTPS_URL -> NoUnderlineURLSpan(string.substring(start, end))
        FoundMatchType.MENTION -> MentionSpan(string.substring(start, end))
        else -> ForegroundColorSpan(colour)
    }
}

private fun isWordCharacters(codePoint: Int): Boolean {
    return (codePoint in 0x30..0x39) || // [0-9]
        (codePoint in 0x41..0x5a) || // [A-Z]
        (codePoint == 0x5f) || // _
        (codePoint in 0x61..0x7a) // [a-z]
}

private fun isValidForTagPrefix(codePoint: Int): Boolean {
    return !(
        isWordCharacters(codePoint) || // \w
            (codePoint == 0x2f) || // /
            (codePoint == 0x29)
        ) // )
}
