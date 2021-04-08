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

package com.keylesspalace.tusky.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.PreferenceManager;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.LinkListener;

import java.net.URI;
import java.net.URISyntaxException;

public class LinkHelper {
    public static String getDomain(String urlString) {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            return "";
        }
        String host = uri.getHost();
        if(host == null) {
            return "";
        } else if (host.startsWith("www.")) {
            return host.substring(4);
        } else {
            return host;
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
    public static void setClickableText(TextView view, CharSequence content,
                                        @Nullable Status.Mention[] mentions, final LinkListener listener) {
        SpannableStringBuilder builder = SpannableStringBuilder.valueOf(content);
        URLSpan[] urlSpans = builder.getSpans(0, content.length(), URLSpan.class);
        for (URLSpan span : urlSpans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            int flags = builder.getSpanFlags(span);
            CharSequence text = builder.subSequence(start, end);
            ClickableSpan customSpan = null;

            if (text.charAt(0) == '#') {
                final String tag = text.subSequence(1, text.length()).toString();
                customSpan = new ClickableSpanNoUnderline() {
                    @Override
                    public void onClick(@NonNull View widget) { listener.onViewTag(tag); }
                };
            } else if (text.charAt(0) == '@' && mentions != null && mentions.length > 0) {
                String accountUsername = text.subSequence(1, text.length()).toString();
                /* There may be multiple matches for users on different instances with the same
                 * username. If a match has the same domain we know it's for sure the same, but if
                 * that can't be found then just go with whichever one matched last. */
                String id = null;
                for (Status.Mention mention : mentions) {
                    if (mention.getLocalUsername().equalsIgnoreCase(accountUsername)) {
                        id = mention.getId();
                        if (mention.getUrl().contains(getDomain(span.getURL()))) {
                            break;
                        }
                    }
                }
                if (id != null) {
                    final String accountId = id;
                    customSpan = new ClickableSpanNoUnderline() {
                        @Override
                        public void onClick(@NonNull View widget) { listener.onViewAccount(accountId); }
                    };
                }
            }

            if (customSpan == null) {
                customSpan = new CustomURLSpan(span.getURL()) {
                    @Override
                    public void onClick(View widget) {
                        listener.onViewUrl(getURL());
                    }
                };
            }
            builder.removeSpan(span);
            builder.setSpan(customSpan, start, end, flags);

            /* Add zero-width space after links in end of line to fix its too large hitbox.
             * See also : https://github.com/tuskyapp/Tusky/issues/846
             *            https://github.com/tuskyapp/Tusky/pull/916 */
            if (end >= builder.length() ||
                    builder.subSequence(end, end + 1).toString().equals("\n")){
                builder.insert(end, "\u200B");
            }
        }

        view.setText(builder);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Put mentions in a piece of text and makes them clickable, associating them with callbacks to
     * notify when they're clicked.
     *
     * @param view the returned text will be put in
     * @param mentions any '@' mentions which are known to be in the content
     * @param listener to notify about particular spans that are clicked
     */
    public static void setClickableMentions(
            TextView view, @Nullable Status.Mention[] mentions, final LinkListener listener) {
        if (mentions == null || mentions.length == 0) {
            view.setText(null);
            return;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int start = 0;
        int end = 0;
        int flags;
        boolean firstMention = true;
        for (Status.Mention mention : mentions) {
            String accountUsername = mention.getLocalUsername();
            final String accountId = mention.getId();
            ClickableSpan customSpan = new ClickableSpanNoUnderline() {
                @Override
                public void onClick(@NonNull View widget) { listener.onViewAccount(accountId); }
            };

            end += 1 + accountUsername.length(); // length of @ + username
            flags = builder.getSpanFlags(customSpan);
            if (firstMention) {
                firstMention = false;
            } else {
                builder.append(" ");
                start += 1;
                end += 1;
            }
            builder.append("@");
            builder.append(accountUsername);
            builder.setSpan(customSpan, start, end, flags);
            builder.append("\u200B"); // same reasonning than in setClickableText
            end += 1; // shift position to take the previous character into account
            start = end;
        }
        view.setText(builder);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static CharSequence createClickableText(String text, String link) {
        URLSpan span = new CustomURLSpan(link);

        SpannableStringBuilder clickableText = new SpannableStringBuilder(text);
        clickableText.setSpan(span, 0, text.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return clickableText;
    }

    /**
     * Opens a link, depending on the settings, either in the browser or in a custom tab
     *
     * @param url a string containing the url to open
     * @param context context
     */
    public static void openLink(String url, Context context) {
        Uri uri = Uri.parse(url).normalizeScheme();

        boolean useCustomTabs = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("customTabs", false);
        if (useCustomTabs) {
            openLinkInCustomTab(uri, context);
        } else {
            openLinkInBrowser(uri, context);
        }
    }

    /**
     * opens a link in the browser via Intent.ACTION_VIEW
     *
     * @param uri the uri to open
     * @param context context
     */
    public static void openLinkInBrowser(Uri uri, Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w("LinkHelper", "Actvity was not found for intent, " + intent);
        }
    }

    /**
     * tries to open a link in a custom tab
     * falls back to browser if not possible
     *
     * @param uri the uri to open
     * @param context context
     */
    public static void openLinkInCustomTab(Uri uri, Context context) {
        int toolbarColor = ThemeUtils.getColor(context, R.attr.colorSurface);
        int navigationbarColor = ThemeUtils.getColor(context, android.R.attr.navigationBarColor);
        int navigationbarDividerColor = ThemeUtils.getColor(context, R.attr.dividerColor);

        CustomTabColorSchemeParams colorSchemeParams = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(toolbarColor)
                .setNavigationBarColor(navigationbarColor)
                .setNavigationBarDividerColor(navigationbarDividerColor)
                .build();

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setShowTitle(true)
                .build();

        try {
            customTabsIntent.launchUrl(context, uri);
        } catch (ActivityNotFoundException e) {
            Log.w("LinkHelper", "Activity was not found for intent " + customTabsIntent);
            openLinkInBrowser(uri, context);
        }

    }

}
