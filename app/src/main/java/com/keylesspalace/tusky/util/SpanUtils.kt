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
import com.keylesspalace.tusky.util.twittertext.Regex
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import java.util.regex.Pattern

/**
 * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/tag.rb">
 *     Tag#HASHTAG_RE</a>.
 */
private const val HASHTAG_SEPARATORS = "_\\u00B7\\u30FB\\u200c"
internal const val TAG_PATTERN_STRING = "(?<![=/)\\p{Alnum}])(#(([\\w_][\\w$HASHTAG_SEPARATORS]*[\\p{Alpha}$HASHTAG_SEPARATORS][\\w$HASHTAG_SEPARATORS]*[\\w_])|([\\w_]*[\\p{Alpha}][\\w_]*)))"
private val TAG_PATTERN = TAG_PATTERN_STRING.toPattern(Pattern.CASE_INSENSITIVE)

/**
 * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/account.rb">
 *     Account#MENTION_RE</a>
 */
private const val USERNAME_PATTERN_STRING = "[a-z0-9_]+([a-z0-9_.-]+[a-z0-9_]+)?"
internal const val MENTION_PATTERN_STRING = "(?<![=/\\w])(@($USERNAME_PATTERN_STRING)(?:@[\\w.-]+[\\w]+)?)"
private val MENTION_PATTERN = MENTION_PATTERN_STRING.toPattern(Pattern.CASE_INSENSITIVE)

private val VALID_URL_PATTERN = Regex.VALID_URL_PATTERN_STRING.toPattern(Pattern.CASE_INSENSITIVE)

private val spanClasses = listOf(ForegroundColorSpan::class.java, URLSpan::class.java)

// url must come first, it may contain the other patterns
val defaultFinders = listOf(
    PatternFinder("http", FoundMatchType.HTTP_URL, VALID_URL_PATTERN),
    PatternFinder("#", FoundMatchType.TAG, TAG_PATTERN),
    PatternFinder("@", FoundMatchType.MENTION, MENTION_PATTERN)
)

enum class FoundMatchType {
    HTTP_URL,
    HTTPS_URL,
    TAG,
    MENTION
}

class PatternFinder(
    val searchString: String,
    val type: FoundMatchType,
    val pattern: Pattern
)

/**
 * Takes text containing mentions and hashtags and urls and makes them the given colour.
 * @param finders The finders to use. This is here so they can be overridden from unit tests.
 */
fun Spannable.highlightSpans(colour: Int, finders: List<PatternFinder> = defaultFinders) {
    // Strip all existing colour spans.
    for (spanClass in spanClasses) {
        clearSpans(spanClass)
    }

    for (finder in finders) {
        // before running the regular expression, check if there is even a chance of it finding something
        if (this.contains(finder.searchString)) {
            val matcher = finder.pattern.matcher(this)

            while (matcher.find()) {
                // we found a match
                val start = matcher.start(1)

                val end = matcher.end(1)

                // only add a span if there is no other one yet (e.g. the #anchor part of an url might match as hashtag, but must be ignored)
                if (this.getSpans(start, end, URLSpan::class.java).isEmpty()) {
                    this.setSpan(
                        getSpan(finder.type, this, colour, start, end),
                        start,
                        end,
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }
}

private fun <T> Spannable.clearSpans(spanClass: Class<T>) {
    for (span in getSpans(0, length, spanClass)) {
        removeSpan(span)
    }
}

/**
 * Replaces text of the form [iconics name] with their spanned counterparts (ImageSpan).
 */
fun addDrawables(text: CharSequence, color: Int, size: Int, context: Context): Spannable {
    val builder = SpannableStringBuilder(text)

    val pattern = Pattern.compile("\\[iconics ([0-9a-z_]+)]")
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

private fun getSpan(
    matchType: FoundMatchType,
    string: CharSequence,
    colour: Int,
    start: Int,
    end: Int
): CharacterStyle {
    return when (matchType) {
        FoundMatchType.HTTP_URL, FoundMatchType.HTTPS_URL -> NoUnderlineURLSpan(string.substring(start, end))
        FoundMatchType.MENTION -> MentionSpan(string.substring(start, end))
        else -> ForegroundColorSpan(colour)
    }
}
