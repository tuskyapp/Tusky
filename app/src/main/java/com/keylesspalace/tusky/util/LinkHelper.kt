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
import android.preference.PreferenceManager
import android.text.ParcelableSpan
import androidx.browser.customtabs.CustomTabsIntent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.View
import android.widget.TextView

import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener

import java.util.HashSet

const val ZERO_WIDTH_SPACE = "\u200B"
private const val TAG = "LinkHelper"

/**
 * Finds links, mentions, and hashtags in a piece of text and makes them clickable, associating
 * them with callbacks to notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param content containing text with mentions, links, or hashtags
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 */
fun setClickableText(view: TextView, content: Spanned, mentions: Array<Status.Mention>?, listener: LinkListener) {
    val builder = SpannableStringBuilder(content)
    highlightSpans(builder, view.linkTextColors.defaultColor, listOf())
    val spans = builder.getSpans(0, content.length, ParcelableSpan::class.java)
    val usedMentionIds = HashSet<String>()

    for (span in spans) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val text = builder.subSequence(start, end)

        val customSpan = when (text[0]) {
            '#' -> getTagSpan(text.substring(1), listener)
            '@' -> {
                if (!mentions.isNullOrEmpty()) {
                    /* There may be multiple matches for users on different instances with the same
                     * username. If a match has the same domain we know it's for sure the same, but if
                     * that can't be found then just go with whichever one matched last. */
                    val id = firstUnusedMention(text.substring(1), mentions, usedMentionIds)
                    if (id != null) {
                        usedMentionIds.add(id)
                        getAccountSpan(id, listener)
                    } else if (span is URLSpan) {
                        // Couldn't even get a fallback account id, just pass through and hope
                        getLinkSpan(span.url, listener)
                    } else {
                        span
                    }
                } else {
                    span
                }
            }
            else -> if (span is URLSpan) {
                getLinkSpan(span.url, listener)
            } else {
                span
            }
        }

        replaceSpan(builder, span, customSpan)
    }

    view.text = builder
    view.linksClickable = true
    view.movementMethod = LinkMovementMethod.getInstance()
}

/**
 * Replace a span within a spannable string builder
 * @param builder the builder to replace spans within
 * @param oldSpan the span to be replaced
 * @param newSpan the new span to be used
 */
private fun replaceSpan(builder: SpannableStringBuilder, oldSpan: ParcelableSpan?, newSpan: Any?) {
    val start = builder.getSpanStart(oldSpan)
    val end = builder.getSpanEnd(oldSpan)
    val flags = builder.getSpanFlags(oldSpan)

    builder.removeSpan(oldSpan)
    builder.setSpan(newSpan, start, end, flags)

    /* Add zero-width space after links in end of line to fix its too large hitbox.
     * See also : https://github.com/tuskyapp/Tusky/issues/846
     *            https://github.com/tuskyapp/Tusky/pull/916 */
    if (end >= builder.length || builder[end] == '\n') {
        builder.insert(end, ZERO_WIDTH_SPACE)
    }
}

/**
 * Returns the first account id with matching username from mentions that isn't contained in usedIds,
 * or the id of the last matching account, if all matching ids are already contained
 * @param username the username to match
 * @param mentions the mentions to search
 * @param usedIds the collection of ids already used
 */
private fun firstUnusedMention(username: String, mentions: Array<Status.Mention>, usedIds: Collection<String>): String? {
    var id: String? = null
    for (mention in mentions) {
        if (mention.localUsername.equals(username, true)) {
            id = mention.id
            if (!usedIds.contains(id)) {
                break
            }
        } else if (mention.username?.startsWith(username, true) == true) {
            // Fall back to full username matching if there's no valid local username match (misskey)
            id = mention.id
        }
    }
    return id
}

private fun getTagSpan(tag: String, listener: LinkListener): ClickableSpan {
    return object : ClickableSpanNoUnderline() {
        override fun onClick(widget: View) {
            listener.onViewTag(tag)
        }
    }
}

private fun getAccountSpan(id: String?, listener: LinkListener): ClickableSpan {
    return object : ClickableSpanNoUnderline() {
        override fun onClick(widget: View) {
            listener.onViewAccount(id)
        }
    }
}

private fun getLinkSpan(url: String, listener: LinkListener): ClickableSpan {
    return object: CustomURLSpan(url) {
        override fun onClick(widget: View?) {
            listener.onViewUrl(url)
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
fun setClickableMentions(view: TextView, mentions: Array<Status.Mention>?, listener: LinkListener) {
    if (mentions.isNullOrEmpty()) {
        view.text = null
        return
    }
    val builder = SpannableStringBuilder()
    var start = 0
    var end = 0
    var firstMention = true

    for (mention in mentions) {
        val accountUsername = mention.localUsername
        val customSpan = getAccountSpan(mention.id, listener)

        end += 1 + accountUsername!!.length // length of @ + username
        val flags = builder.getSpanFlags(customSpan)
        if (firstMention) {
            firstMention = false
        } else {
            builder.append(" ")
            start += 1
            end += 1
        }
        builder.append("@")
        builder.append(accountUsername)
        builder.setSpan(customSpan, start, end, flags)
        builder.append(ZERO_WIDTH_SPACE) // same reasoning as in setClickableText
        end += 1 // shift position to take the previous character into account
        start = end
    }
    view.text = builder
}

/**
 * Opens a link, depending on the settings, either in the browser or in a custom tab
 *
 * @param url a string containing the url to open
 * @param context context
 */
fun openLink(url: String?, context: Context) {
    val uri = Uri.parse(url).normalizeScheme()

    val useCustomTabs = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("customTabs", false)
    if (useCustomTabs) {
        openLinkInCustomTab(uri, context)
    } else {
        openLinkInBrowser(uri, context)
    }
}

/**
 * opens a link in the browser via Intent.ACTION_VIEW
 *
 * @param uri the uri to open
 * @param context context
 */
fun openLinkInBrowser(uri: Uri, context: Context) {
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
fun openLinkInCustomTab(uri: Uri, context: Context) {
    val toolbarColor = ThemeUtils.getColorById(context, "custom_tab_toolbar")

    val builder = CustomTabsIntent.Builder()
    builder.setToolbarColor(toolbarColor)
    val customTabsIntent = builder.build()
    try {
        val packageName = CustomTabsHelper.getPackageNameToUse(context)

        //If we cant find a package name, it means theres no browser that supports
        //Chrome Custom Tabs installed. So, we fallback to the webview
        if (packageName == null) {
            openLinkInBrowser(uri, context)
        } else {
            customTabsIntent.intent.setPackage(packageName)
            customTabsIntent.launchUrl(context, uri)
        }
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity was not found for intent, $customTabsIntent")
    }

}
