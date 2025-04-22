/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */
@file:JvmName("LinkHelper")

package com.keylesspalace.tusky.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.R as materialR
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status.Mention
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.settings.PrefKeys
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

fun getDomain(urlString: String?): String {
    val host = urlString?.toUri()?.host
    return when {
        host == null -> ""
        host.startsWith("www.") -> host.substring(4)
        else -> host
    }
}

/**
 * Finds links, mentions, and hashtags in a piece of text and makes them clickable, associating
 * them with callbacks to notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param content containing text with mentions, links, or hashtags
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 * @param trailingHashtagView a text view to fill with trailing / out-of-band hashtags
 */
fun setClickableText(
    view: TextView,
    content: CharSequence,
    mentions: List<Mention>,
    tags: List<HashTag>?,
    listener: LinkListener,
    trailingHashtagView: TextView? = null,
) {
    val spannableContent = markupHiddenUrls(view, content)
    val (endOfContent, trailingHashtags) = when {
        trailingHashtagView == null || tags.isNullOrEmpty() -> Pair(spannableContent.length, emptyList())
        else -> getTrailingHashtags(spannableContent)
    }
    val inlineHashtags = mutableSetOf<CharSequence>()

    view.text = spannableContent.apply {
        styleQuoteSpans(view)
        getSpans(0, endOfContent, URLSpan::class.java).forEach { span ->
            val start = getSpanStart(span)
            if (get(start) == '#') {
                inlineHashtags.add(normalizeToASCII(subSequence(start + 1, getSpanEnd(span))))
            }
            setClickableText(span, this, mentions, tags, listener)
        }
    }.subSequence(0, endOfContent).trimEnd()

    view.movementMethod = NoTrailingSpaceLinkMovementMethod

    val showHashtagBar = (trailingHashtags.isNotEmpty() || inlineHashtags.size != tags?.size)
    // I don't _love_ setting the visibility here, but the alternative is to duplicate the logic in other places
    trailingHashtagView?.visible(showHashtagBar)

    if (showHashtagBar) {
        trailingHashtagView?.apply {
            text = buildTrailingHashtagText(
                tags?.filterNot { tag -> inlineHashtags.any { it.contentEquals(tag.name, ignoreCase = true) } },
                trailingHashtags,
                listener,
            )
        }
    }
}

/**
 * Build a spanned string containing trailing and out-of-band hashtags for the trailing hashtag view
 * @param tagsFromServer The list of hashtags from the server
 * @param trailingHashtagsFromContent The list of trailing hashtags scraped from the post content
 * @param listener to notify about particular spans that are clicked
 */
private fun buildTrailingHashtagText(tagsFromServer: List<HashTag>?, trailingHashtagsFromContent: List<HashTag>, listener: LinkListener): SpannableStringBuilder = SpannableStringBuilder().apply {
    // we apply the tags scraped from the content first to preserve the casing
    // (tags from the server are often downcased)
    val additionalTags = tagsFromServer?.let {
        it.filter { serverTag ->
            trailingHashtagsFromContent.none {
                serverTag.name.equals(normalizeToASCII(it.name), ignoreCase = true)
            }
        }
    } ?: emptyList()
    appendTags(trailingHashtagsFromContent.plus(additionalTags), listener)
}

/**
 * Append space-separated url spans for a list of hashtags
 * @param tags The tags to append
 * @param listener to notify about particular spans that are clicked
 */
private fun SpannableStringBuilder.appendTags(tags: List<HashTag>, listener: LinkListener) {
    tags.forEachIndexed { index, tag ->
        append("#${tag.name}", getCustomSpanForTag(normalizeToASCII(tag.name), URLSpan(tag.url), listener), 0)
        if (index != tags.lastIndex) {
            append(" ")
        }
    }
}

private val hashtagWithHashPattern = Pattern.compile("^#$HASHTAG_EXPRESSION$")
private val whitespacePattern = Regex("""\s+""")

/**
 * Find the "trailing" hashtags in spanned content
 * These are hashtags in lines consisting *only* of hashtags at the end of the post
 */
@VisibleForTesting
internal fun getTrailingHashtags(content: Spanned): Pair<Int, List<HashTag>> {
    // split() instead of lines() because we need to be able to account for the length of the removed delimiter
    val trailingContentLength = content.split('\r', '\n').asReversed().takeWhile { line ->
        line.splitToSequence(whitespacePattern).all { it.isBlank() || hashtagWithHashPattern.matcher(it).matches() }
    }.sumOf { it.length + 1 } // length + 1 to include the stripped line ending character

    return when (trailingContentLength) {
        0 -> Pair(content.length, emptyList())
        else -> {
            val trailingContentOffset = (content.length - trailingContentLength).coerceAtLeast(0)
            Pair(
                trailingContentOffset,
                content.getSpans(trailingContentOffset, content.length, URLSpan::class.java)
                    .filter { content[content.getSpanStart(it)] == '#' } // just in case
                    .map { spanToHashtag(content, it) }
            )
        }
    }
}

// URLSpan("#tag", url) -> Hashtag("tag", url)
private fun spanToHashtag(content: Spanned, span: URLSpan) = HashTag(
    content.subSequence(content.getSpanStart(span) + 1, content.getSpanEnd(span)).toString(),
    span.url,
)

@VisibleForTesting
internal fun markupHiddenUrls(view: TextView, content: CharSequence): SpannableStringBuilder {
    val spannableContent = SpannableStringBuilder(content)
    val originalSpans = spannableContent.getSpans(0, content.length, URLSpan::class.java)
    val obscuredLinkSpans = originalSpans.filter {
        val start = spannableContent.getSpanStart(it)
        val firstCharacter = content[start]
        return@filter if (firstCharacter == '#' || firstCharacter == '@') {
            false
        } else {
            val text = spannableContent.subSequence(
                start,
                spannableContent.getSpanEnd(it)
            ).toString()
                .split(' ').lastOrNull().orEmpty()
            var textDomain = getDomain(text)
            if (textDomain.isBlank()) {
                textDomain = getDomain("https://$text")
            }
            getDomain(it.url) != textDomain
        }
    }

    for (span in obscuredLinkSpans) {
        val start = spannableContent.getSpanStart(span)
        val end = spannableContent.getSpanEnd(span)
        val additionalText = " " + view.context.getString(
            R.string.url_domain_notifier,
            getDomain(span.url)
        )
        spannableContent.insert(
            end,
            additionalText
        )
        // reinsert the span so it covers the original and the additional text
        spannableContent.setSpan(span, start, end + additionalText.length, 0)

        val linkDrawable = AppCompatResources.getDrawable(view.context, R.drawable.ic_open_in_new_24dp)!!
        // ImageSpan does not always align the icon correctly in the line, let's use our custom emoji span for this
        val linkDrawableSpan = EmojiSpan(view)
        linkDrawableSpan.imageDrawable = linkDrawable

        val placeholderIndex = end + 2

        spannableContent.setSpan(
            linkDrawableSpan,
            placeholderIndex,
            placeholderIndex + "🔗".length,
            0
        )
    }

    return spannableContent
}

@VisibleForTesting
fun setClickableText(
    span: URLSpan,
    builder: SpannableStringBuilder,
    mentions: List<Mention>,
    tags: List<HashTag>?,
    listener: LinkListener
) = builder.apply {
    val start = getSpanStart(span)
    val end = getSpanEnd(span)
    val flags = getSpanFlags(span)
    val text = subSequence(start, end)

    val customSpan = when (text[0]) {
        '#' -> getCustomSpanForTag(text, tags, span, listener)
        '@' -> getCustomSpanForMention(mentions, span, listener)
        else -> null
    } ?: object : NoUnderlineURLSpan(span.url) {
        override fun onClick(view: View) = listener.onViewUrl(url)
    }

    removeSpan(span)
    setSpan(customSpan, start, end, flags)
}

@VisibleForTesting
fun getTagName(text: CharSequence, tags: List<HashTag>?): String? {
    val scrapedName = normalizeToASCII(text.subSequence(1, text.length))
    return when (tags) {
        null -> scrapedName
        else -> tags.firstOrNull { it.name.equals(scrapedName, true) }?.name
    }
}

private fun getCustomSpanForTag(
    text: CharSequence,
    tags: List<HashTag>?,
    span: URLSpan,
    listener: LinkListener
) = getTagName(text, tags)?.let { getCustomSpanForTag(it, span, listener) }

private fun getCustomSpanForTag(
    tagName: String,
    span: URLSpan,
    listener: LinkListener
) = object : NoUnderlineURLSpan(span.url) {
    override fun onClick(view: View) = listener.onViewTag(tagName)
}

private fun getCustomSpanForMention(
    mentions: List<Mention>,
    span: URLSpan,
    listener: LinkListener
): ClickableSpan? {
    // https://github.com/tuskyapp/Tusky/pull/2339
    return mentions.firstOrNull { it.url == span.url }?.let {
        getCustomSpanForMentionUrl(span.url, it.id, listener)
    }
}

private fun getCustomSpanForMentionUrl(
    url: String,
    mentionId: String,
    listener: LinkListener
): ClickableSpan = object : MentionSpan(url) {
    override fun onClick(view: View) = listener.onViewAccount(mentionId)
}

private fun SpannableStringBuilder.styleQuoteSpans(view: TextView) {
    getSpans(0, length, QuoteSpan::class.java).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        val flags = getSpanFlags(span)

        val quoteColor = MaterialColors.getColor(view, android.R.attr.textColorTertiary)

        val newQuoteSpan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            QuoteSpan(
                quoteColor,
                Utils.dpToPx(view.context, 3),
                Utils.dpToPx(view.context, 8)
            )
        } else {
            QuoteSpan(quoteColor)
        }

        val quoteColorSpan = ForegroundColorSpan(quoteColor)

        removeSpan(span)
        setSpan(newQuoteSpan, start, end, flags)
        setSpan(quoteColorSpan, start, end, flags)
    }
}

/**
 * Put mentions in a piece of text and makes them clickable, associating them with callbacks to
 * notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 */
fun setClickableMentions(view: TextView, mentions: List<Mention>?, listener: LinkListener) {
    if (mentions?.isEmpty() != false) {
        view.text = null
        return
    }

    view.text = SpannableStringBuilder().apply {
        var start = 0
        var end = 0
        var flags: Int
        var firstMention = true

        for (mention in mentions) {
            val customSpan = getCustomSpanForMentionUrl(mention.url, mention.id, listener)
            end += 1 + mention.localUsername.length // length of @ + username
            flags = getSpanFlags(customSpan)
            if (firstMention) {
                firstMention = false
            } else {
                append(" ")
                start += 1
                end += 1
            }

            append("@")
            append(mention.localUsername)
            setSpan(customSpan, start, end, flags)
            start = end
        }
    }
    view.movementMethod = NoTrailingSpaceLinkMovementMethod
}

fun createClickableText(text: String, link: String): CharSequence = SpannableStringBuilder(text).apply {
    setSpan(NoUnderlineURLSpan(link), 0, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
}

/**
 * Opens a link, depending on the settings, either in the browser or in a custom tab
 *
 * @receiver the Context to open the link from
 * @param url a string containing the url to open
 */
fun Context.openLink(url: String) {
    val uri = url.toUri().normalizeScheme()
    val useCustomTabs = PreferenceManager.getDefaultSharedPreferences(
        this
    ).getBoolean(PrefKeys.CUSTOM_TABS, false)

    if (useCustomTabs) {
        openLinkInCustomTab(uri, this)
    } else {
        openLinkInBrowser(uri, this)
    }
}

/**
 * opens a link in the browser via Intent.ACTION_VIEW
 *
 * @param uri the uri to open
 * @param context context
 */
private fun openLinkInBrowser(uri: Uri?, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity was not found for intent, $intent")
    }
}

/**
 * tries to open a link in a custom tab
 * falls back to browser if not possible
 *
 * @param uri the uri to open
 * @param context context
 */
fun openLinkInCustomTab(uri: Uri, context: Context) {
    val toolbarColor = MaterialColors.getColor(
        context,
        materialR.attr.colorSurface,
        Color.BLACK
    )
    val navigationbarColor = MaterialColors.getColor(
        context,
        android.R.attr.navigationBarColor,
        Color.BLACK
    )
    val navigationbarDividerColor = MaterialColors.getColor(
        context,
        R.attr.dividerColor,
        Color.BLACK
    )
    val colorSchemeParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .setNavigationBarColor(navigationbarColor)
        .setNavigationBarDividerColor(navigationbarDividerColor)
        .build()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorSchemeParams)
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .setShowTitle(true)
        .build()

    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity was not found for intent $customTabsIntent")
        openLinkInBrowser(uri, context)
    }
}

// https://mastodon.foo.bar/@User
// https://mastodon.foo.bar/@User/43456787654678
// https://mastodon.foo.bar/users/User/statuses/43456787654678
// https://pleroma.foo.bar/users/User
// https://pleroma.foo.bar/users/9qTHT2ANWUdXzENqC0
// https://pleroma.foo.bar/notice/9sBHWIlwwGZi5QGlHc
// https://pleroma.foo.bar/objects/d4643c42-3ae0-4b73-b8b0-c725f5819207
// https://friendica.foo.bar/profile/user
// https://friendica.foo.bar/display/d4643c42-3ae0-4b73-b8b0-c725f5819207
// https://misskey.foo.bar/notes/83w6r388br (always lowercase)
// https://pixelfed.social/p/connyduck/391263492998670833
// https://pixelfed.social/connyduck
// https://gts.foo.bar/@goblin/statuses/01GH9XANCJ0TA8Y95VE9H3Y0Q2
// https://gts.foo.bar/@goblin
// https://foo.microblog.pub/o/5b64045effd24f48a27d7059f6cb38f5
// https://bookwyrm.foo.bar/user/User
// https://bookwyrm.foo.bar/user/User/comment/123456
fun looksLikeMastodonUrl(urlString: String): Boolean {
    val uri: URI
    try {
        uri = URI(urlString)
    } catch (e: URISyntaxException) {
        return false
    }

    if (uri.query != null ||
        uri.fragment != null ||
        uri.path == null
    ) {
        return false
    }

    return uri.path.let {
        it.matches("^/@[^/]+$".toRegex()) ||
            it.matches("^/@[^/]+/\\d+$".toRegex()) ||
            it.matches("^/users/[^/]+/statuses/\\d+$".toRegex()) ||
            it.matches("^/users/\\w+$".toRegex()) ||
            it.matches("^/user/[^/]+/comment/\\d+$".toRegex()) ||
            it.matches("^/user/\\w+$".toRegex()) ||
            it.matches("^/notice/[a-zA-Z0-9]+$".toRegex()) ||
            it.matches("^/objects/[-a-f0-9]+$".toRegex()) ||
            it.matches("^/notes/[a-z0-9]+$".toRegex()) ||
            it.matches("^/display/[-a-f0-9]+$".toRegex()) ||
            it.matches("^/profile/\\w+$".toRegex()) ||
            it.matches("^/p/\\w+/\\d+$".toRegex()) ||
            it.matches("^/\\w+$".toRegex()) ||
            it.matches("^/@[^/]+/statuses/[a-zA-Z0-9]+$".toRegex()) ||
            it.matches("^/o/[a-f0-9]+$".toRegex())
    }
}

private const val TAG = "LinkHelper"

/**
 * [LinkMovementMethod] that doesn't add a leading/trailing clickable area.
 *
 * [LinkMovementMethod] has a bug in its calculation of the clickable width of a span on a line. If
 * the span is the last thing on the line the clickable area extends to the end of the view. So the
 * user can tap what appears to be whitespace and open a link.
 *
 * Fix this by overriding ACTION_UP touch events and calculating the true start and end of the
 * content on the line that was tapped. Then ignore clicks that are outside this area.
 *
 * See https://github.com/tuskyapp/Tusky/issues/1567.
 */
object NoTrailingSpaceLinkMovementMethod : LinkMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action
        if (action != ACTION_UP) return super.onTouchEvent(widget, buffer, event)

        val x = event.x.toInt()
        val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
        val line = widget.layout.getLineForVertical(y)
        val lineLeft = widget.layout.getLineLeft(line)
        val lineRight = widget.layout.getLineRight(line)
        if (x > lineRight || x >= 0 && x < lineLeft) {
            return true
        }

        return super.onTouchEvent(widget, buffer, event)
    }
}
