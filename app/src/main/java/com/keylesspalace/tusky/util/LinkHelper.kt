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
 * see <http://www.gnu.org/licenses>. */
@file:JvmName("LinkHelper")

package com.keylesspalace.tusky.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status.Mention
import com.keylesspalace.tusky.interfaces.LinkListener
import java.net.URI
import java.net.URISyntaxException

fun getDomain(urlString: String?): String {
    val uri: URI = try {
        URI(urlString)
    } catch (e: URISyntaxException) {
        return ""
    }

    val host = uri.host
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
 */
fun setClickableText(view: TextView, content: CharSequence, mentions: List<Mention>, tags: List<HashTag>, listener: LinkListener) {
    val builder = SpannableStringBuilder.valueOf(content)
    val urlSpans = builder.getSpans(0, content.length, URLSpan::class.java)

    for (span in urlSpans) {
        setClickableText(span, builder, mentions, tags, listener)
    }
    view.text = builder
    view.movementMethod = LinkMovementMethod.getInstance()
}

@VisibleForTesting
fun setClickableText(
    span: URLSpan,
    builder: SpannableStringBuilder,
    mentions: List<Mention>,
    tags: List<HashTag>,
    listener: LinkListener
) {
    val start = builder.getSpanStart(span)
    val end = builder.getSpanEnd(span)
    val flags = builder.getSpanFlags(span)
    val text = builder.subSequence(start, end)

    val customSpan = when {
        text[0] == '#' -> getCustomSpanForTag(tags, span, listener)
        text[0] == '@' -> getCustomSpanForMention(mentions, span, listener)
        else -> null
    } ?: object : NoUnderlineURLSpan(span.url) {
        override fun onClick(view: View) {
            listener.onViewUrl(url)
        }
    }

    builder.removeSpan(span)
    builder.setSpan(customSpan, start, end, flags)

    /* Add zero-width space after links in end of line to fix its too large hitbox.
     * See also : https://github.com/tuskyapp/Tusky/issues/846
     *            https://github.com/tuskyapp/Tusky/pull/916 */
    if (end >= builder.length || builder.subSequence(end, end + 1).toString() == "\n") {
        builder.insert(end, "\u200B")
    }
}

private fun getCustomSpanForTag(tags: List<HashTag>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    return when (val tag = tags?.firstOrNull { it.url == span.url }) {
        null -> null
        else -> object : NoUnderlineURLSpan(span.url) {
            override fun onClick(view: View) {
                listener.onViewTag(tag.name)
            }
        }
    }
}

private fun getCustomSpanForMention(mentions: List<Mention>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    // https://github.com/tuskyapp/Tusky/pull/2339
    return when (val mention = mentions?.firstOrNull { it.url == span.url }) {
        null -> null
        else -> getCustomSpanForMentionUrl(span.url, mention.id, listener)
    }
}

private fun getCustomSpanForMentionUrl(url: String, mentionId: String, listener: LinkListener): ClickableSpan {
    return object : NoUnderlineURLSpan(url) {
        override fun onClick(view: View) {
            listener.onViewAccount(mentionId)
        }
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

    val builder = SpannableStringBuilder()
    var start = 0
    var end = 0
    var flags: Int
    var firstMention = true

    for (mention in mentions) {
        val customSpan = getCustomSpanForMentionUrl(mention.url, mention.id, listener)
        end += 1 + mention.username.length // length of @ + username
        flags = builder.getSpanFlags(customSpan)
        if (firstMention) {
            firstMention = false
        } else {
            builder.append(" ")
            start += 1
            end += 1
        }

        builder.apply {
            append("@")
            append(mention.username)
            setSpan(customSpan, start, end, flags)
            append("\u200B") // same reasoning as in setClickableText
        }
        end += 1 // shift position to take the previous character into account
        start = end
    }

    view.text = builder
    view.movementMethod = LinkMovementMethod.getInstance()
}

fun createClickableText(text: String, link: String): CharSequence {
    return SpannableStringBuilder(text).apply {
        setSpan(NoUnderlineURLSpan(link), 0, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
}

/**
 * Opens a link, depending on the settings, either in the browser or in a custom tab
 *
 * @param url a string containing the url to open
 * @param context context
 */
fun Context.openLink(url: String) {
    val uri = url.toUri().normalizeScheme()
    val useCustomTabs = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("customTabs", false)

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
        Log.w(TAG, "Actvity was not found for intent, $intent")
    }
}

/**
 * tries to open a link in a custom tab
 * falls back to browser if not possible
 *
 * @param uri the uri to open
 * @param context context
 */
private fun openLinkInCustomTab(uri: Uri, context: Context) {
    val toolbarColor = ThemeUtils.getColor(context, R.attr.colorSurface)
    val navigationbarColor = ThemeUtils.getColor(context, android.R.attr.navigationBarColor)
    val navigationbarDividerColor = ThemeUtils.getColor(context, R.attr.dividerColor)
    val colorSchemeParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .setNavigationBarColor(navigationbarColor)
        .setNavigationBarDividerColor(navigationbarDividerColor)
        .build()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorSchemeParams)
        .setShowTitle(true)
        .build()

    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity was not found for intent $customTabsIntent")
        openLinkInBrowser(uri, context)
    }
}

private const val TAG = "LinkHelper"
