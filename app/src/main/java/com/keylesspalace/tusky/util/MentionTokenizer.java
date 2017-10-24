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

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.MultiAutoCompleteTextView;

public class MentionTokenizer implements MultiAutoCompleteTextView.Tokenizer {
    @Override
    public int findTokenStart(CharSequence text, int cursor) {
        int i = cursor;
        while (i > 0 && text.charAt(i - 1) != '@') {
            if (!Character.isLetterOrDigit(text.charAt(i - 1))) return cursor;
            i--;
        }
        if (i < 1 || text.charAt(i - 1) != '@') {
            return cursor;
        }
        return i;
    }

    @Override
    public int findTokenEnd(CharSequence text, int cursor) {
        int i = cursor;
        int length = text.length();
        while (i < length) {
            if (text.charAt(i) == ' ') {
                return i;
            } else {
                i++;
            }
        }
        return length;
    }

    @Override
    public CharSequence terminateToken(CharSequence text) {
        int i = text.length();
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        if (i > 0 && text.charAt(i - 1) == ' ') {
            return text;
        } else if (text instanceof Spanned) {
            SpannableString s = new SpannableString(text + " ");
            TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, s, 0);
            return s;
        } else {
            return text + " ";
        }
    }
}
