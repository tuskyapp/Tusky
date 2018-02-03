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
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.LinkListener;

import java.net.URI;
import java.net.URISyntaxException;

public class LinkHelper {
    private static String getDomain(String urlString) {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            return "";
        }
        String host = uri.getHost();
        if (host.startsWith("www.")) {
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
    public static void setClickableText(TextView view, Spanned content,
            @Nullable Status.Mention[] mentions, final LinkListener listener) {

        SpannableStringBuilder builder = new SpannableStringBuilder(content);
        URLSpan[] urlSpans = content.getSpans(0, content.length(), URLSpan.class);
        for (URLSpan span : urlSpans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            int flags = builder.getSpanFlags(span);
            CharSequence text = builder.subSequence(start, end);
            if (text.charAt(0) == '#') {
                final String tag = text.subSequence(1, text.length()).toString();
                ClickableSpan newSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        listener.onViewTag(tag);
                    }
                    @Override public void updateDrawState(TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                    }
                };
                builder.removeSpan(span);
                builder.setSpan(newSpan, start, end, flags);
            } else if (text.charAt(0) == '@' && mentions != null && mentions.length > 0) {
                String accountUsername = text.subSequence(1, text.length()).toString();
                /* There may be multiple matches for users on different instances with the same
                 * username. If a match has the same domain we know it's for sure the same, but if
                 * that can't be found then just go with whichever one matched last. */
                String id = null;
                for (Status.Mention mention : mentions) {
                    if (mention.localUsername.equalsIgnoreCase(accountUsername)) {
                        id = mention.id;
                        if (mention.url.contains(getDomain(span.getURL()))) {
                            break;
                        }
                    }
                }
                if (id != null) {
                    final String accountId = id;
                    ClickableSpan newSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            listener.onViewAccount(accountId);
                        }
                        @Override public void updateDrawState(TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setUnderlineText(false);
                        }
                    };
                    builder.removeSpan(span);
                    builder.setSpan(newSpan, start, end, flags);
                } else {
                    ClickableSpan newSpan = new CustomURLSpan(span.getURL());
                    builder.removeSpan(span);
                    builder.setSpan(newSpan, start, end, flags);
                }
            } else {
                ClickableSpan newSpan = new CustomURLSpan(span.getURL());
                builder.removeSpan(span);
                builder.setSpan(newSpan, start, end, flags);
            }
        }
        view.setText(builder);
        view.setLinksClickable(true);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Opens a link, depending on the settings, either in the browser or in a custom tab
     *
     * @param url a string containing the url to open
     * @param context context
     */
    public static void openLink(String url, Context context) {
        Uri uri = Uri.parse(url);

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
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w("URLSpan", "Actvity was not found for intent, " + intent.toString());
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
        int toolbarColor = ThemeUtils.getColorById(context, "custom_tab_toolbar");

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(toolbarColor);
        CustomTabsIntent customTabsIntent = builder.build();
        try {
            String packageName = CustomTabsHelper.getPackageNameToUse(context);

            //If we cant find a package name, it means theres no browser that supports
            //Chrome Custom Tabs installed. So, we fallback to the webview
            if (packageName == null) {
                openLinkInBrowser(uri, context);
            } else {
                customTabsIntent.intent.setPackage(packageName);
                customTabsIntent.launchUrl(context, uri);
            }
        } catch (ActivityNotFoundException e) {
            Log.w("URLSpan", "Activity was not found for intent, " + customTabsIntent.toString());
        }

    }



}
