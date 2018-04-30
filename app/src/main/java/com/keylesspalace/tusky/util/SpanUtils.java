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

import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpanUtils {
    /**
     * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/tag.rb">
     *     Tag#HASHTAG_RE</a>.
     */
    private static final String TAG_REGEX = "(?:^|[^/)\\w])#([\\w_]*[\\p{Alpha}_][\\w_]*)";
    /**
     * @see <a href="https://github.com/tootsuite/mastodon/blob/master/app/models/account.rb">
     *     Account#MENTION_RE</a>
     */
    private static final String MENTION_REGEX =
            "(?:^|[^/[:word:]])@([a-z0-9_]+(?:@[a-z0-9\\.\\-]+[a-z0-9]+)?)";
    private static final String URL_REGEX = "(?:(^|\\b)https?://[^\\p{Whitespace}]+)";

    private enum FoundMatchType {
        URL,
        TAG,
        MENTION,
    }
    static Map<FoundMatchType, PatternFinder> finders;

    private static class PatternFinder {
        char searchCharacter;
        Pattern pattern;
        int searchPrefixWidth;

        PatternFinder(char searchCharacter, String regex, int searchPrefixWidth) {
            this.searchCharacter = searchCharacter;
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.searchPrefixWidth = searchPrefixWidth;
        }
    }

    static {
        finders = new HashMap<>();
        finders.put(FoundMatchType.URL, new PatternFinder(':', URL_REGEX, 6));
        finders.put(FoundMatchType.TAG, new PatternFinder('#', TAG_REGEX, 1));
        finders.put(FoundMatchType.MENTION, new PatternFinder('@', MENTION_REGEX, 1));
    }

    private static class FindCharsResult {
        FoundMatchType matchType;
        int stringIndex;

        FindCharsResult() {
            stringIndex = -1;
        }
    }

    private static FindCharsResult findChars(String string, int fromIndex) {
        FindCharsResult result = new FindCharsResult();
        final int length = string.length();
        for (int i = fromIndex; i < length; i++) {
            char c = string.charAt(i);
            for (FoundMatchType matchType : FoundMatchType.values()) {
                PatternFinder finder = finders.get(matchType);
                if (finder.searchCharacter == c
                        && (i < finder.searchPrefixWidth ||
                            Character.isWhitespace(string.codePointAt(i - finder.searchPrefixWidth)))) {
                    result.matchType = matchType;
                    result.stringIndex = i;
                    return result;
                }
            }
        }
        return result;
    }

    private static FindCharsResult findStart(String string, int fromIndex) {
        for (int length = string.length(); fromIndex < length; ++fromIndex) {
            FindCharsResult found = findChars(string, fromIndex);
            int i = found.stringIndex;
            if (i < 0) {
                break;
            }
            return found;
        }
        return new FindCharsResult();
    }

    private static int findEndOfPattern(@NonNull String string, int startIndex, @NonNull Pattern pattern)
    {
        Matcher matcher = pattern.matcher(string);
        return (matcher.find(startIndex) ? matcher.end() : -1);
    }

    private static CharacterStyle getSpan(FoundMatchType matchType, String string, int colour, int start, int end) {
        return (matchType == FoundMatchType.URL) ?
            new CustomURLSpan(string.substring(start, end)) :
            new ForegroundColorSpan(colour);
    }

    private static <T> void clearSpans(Spannable text, Class<T> spanClass) {
        for(T span : text.getSpans(0, text.length(), spanClass)) {
            text.removeSpan(span);
        }
    }

    static final Class[] spanClasses = {ForegroundColorSpan.class, URLSpan.class};

    /** Takes text containing mentions and hashtags and urls and makes them the given colour. */
    public static void highlightSpans(Spannable text, int colour) {
        // Strip all existing colour spans.
        for (Class spanClass : spanClasses) {
            clearSpans(text, spanClass);
        }

        // Colour the mentions and hashtags.
        String string = text.toString();
        int n = text.length();
        int start = 0;
        int end = 0;
        while (end >= 0 && end < n && start >= 0) {
            // Search for url first because it can contain the other characters
            FindCharsResult found = findStart(string, end);
            start = found.stringIndex;
            if (start >= 0) {
                PatternFinder finder = finders.get(found.matchType);
                int aNewStart = Math.max(0, start - finder.searchPrefixWidth);
                end = findEndOfPattern(string, Math.max(0, aNewStart), finder.pattern);
                if (end >= 0) {
                    text.setSpan(getSpan(found.matchType, string, colour, aNewStart, end), aNewStart, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }
}
