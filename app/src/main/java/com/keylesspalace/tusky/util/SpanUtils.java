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

import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpanUtils {
    /**
     * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/tag.rb">
     *     Tag#HASHTAG_RE</a>.
     */
    private static final String TAG_REGEX = "(?:^|[^/)\\w])#([\\w_]*[\\p{Alpha}_][\\w_]*)";
    private static Pattern TAG_PATTERN = Pattern.compile(TAG_REGEX, Pattern.CASE_INSENSITIVE);
    /**
     * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/account.rb">
     *     Account#MENTION_RE</a>
     */
    private static final String MENTION_REGEX =
            "(?:^|[^/[:word:]])@([a-z0-9_]+(?:@[a-z0-9\\.\\-]+[a-z0-9]+)?)";
    private static Pattern MENTION_PATTERN =
            Pattern.compile(MENTION_REGEX, Pattern.CASE_INSENSITIVE);

    private static class FindCharsResult {
        int charIndex;
        int stringIndex;

        FindCharsResult() {
            charIndex = -1;
            stringIndex = -1;
        }
    }

    private static FindCharsResult findChars(String string, int fromIndex, char[] chars) {
        FindCharsResult result = new FindCharsResult();
        final int length = string.length();
        for (int i = fromIndex; i < length; i++) {
            char c = string.charAt(i);
            for (int j = 0; j < chars.length; j++) {
                if (chars[j] == c) {
                    result.charIndex = j;
                    result.stringIndex = i;
                    return result;
                }
            }
        }
        return result;
    }

    private static FindCharsResult findStart(String string, int fromIndex, char[] chars) {
        final int length = string.length();
        while (fromIndex < length) {
            FindCharsResult found = findChars(string, fromIndex, chars);
            int i = found.stringIndex;
            if (i < 0) {
                break;
            } else if (i == 0 || i >= 1 && Character.isWhitespace(string.codePointBefore(i))) {
                return found;
            } else {
                fromIndex = i + 1;
            }
        }
        return new FindCharsResult();
    }

    private static int findEndOfHashtag(String string, int fromIndex) {
        Matcher matcher = TAG_PATTERN.matcher(string);
        if (fromIndex >= 1) {
            fromIndex--;
        }
        boolean found = matcher.find(fromIndex);
        if (found) {
            return matcher.end();
        } else {
            return -1;
        }
    }

    private static int findEndOfMention(String string, int fromIndex) {
        Matcher matcher = MENTION_PATTERN.matcher(string);
        if (fromIndex >= 1) {
            fromIndex--;
        }
        boolean found = matcher.find(fromIndex);
        if (found) {
            return matcher.end();
        } else {
            return -1;
        }
    }

    /** Takes text containing mentions and hashtags and makes them the given colour. */
    public static void highlightSpans(Spannable text, int colour) {
        // Strip all existing colour spans.
        int n = text.length();
        ForegroundColorSpan[] oldSpans = text.getSpans(0, n, ForegroundColorSpan.class);
        for (int i = oldSpans.length - 1; i >= 0; i--) {
            text.removeSpan(oldSpans[i]);
        }
        // Colour the mentions and hashtags.
        String string = text.toString();
        int start;
        int end = 0;
        while (end < n) {
            char[] chars = { '#', '@' };
            FindCharsResult found = findStart(string, end, chars);
            start = found.stringIndex;
            if (start < 0) {
                break;
            }
            if (found.charIndex == 0) {
                end = findEndOfHashtag(string, start);
            } else if (found.charIndex == 1) {
                end = findEndOfMention(string, start);
            } else {
                break;
            }
            if (end < 0) {
                break;
            }
            text.setSpan(new ForegroundColorSpan(colour), start, end,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
    }
}
