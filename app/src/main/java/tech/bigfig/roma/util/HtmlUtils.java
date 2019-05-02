/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.util;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

public class HtmlUtils {
    private static CharSequence trimTrailingWhitespace(CharSequence s) {
        int i = s.length();
        do {
            i--;
        } while (i >= 0 && Character.isWhitespace(s.charAt(i)));
        return s.subSequence(0, i + 1);
    }

    public static Spanned fromHtml(String html) {
        Spanned result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(html);
        }
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * all status contents do, so it should be trimmed. */
        return (Spanned) trimTrailingWhitespace(result);
    }

    public static String toHtml(Spanned text) {
        String result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result = Html.toHtml(text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        } else {
            result = Html.toHtml(text);
        }
        return result;
    }
}
