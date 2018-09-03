/*
 * Copyright 2018 Diego Rossi (@_HellPie)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.keylesspalace.tusky.util;

import android.text.InputFilter;
import android.text.Spanned;

public class SmartLengthInputFilter implements InputFilter {

	private final int max;

	public SmartLengthInputFilter(int max) {
		this.max = max;
	}

	/**
	 * This method is called when the buffer is going to replace the
	 * range <code>dstart &hellip; dend</code> of <code>dest</code>
	 * with the new text from the range <code>start &hellip; end</code>
	 * of <code>source</code>.  Return the CharSequence that you would
	 * like to have placed there instead, including an empty string
	 * if appropriate, or <code>null</code> to accept the original
	 * replacement.  Be careful to not to reject 0-length replacements,
	 * as this is what happens when you delete text.  Also beware that
	 * you should not attempt to make any changes to <code>dest</code>
	 * from this method; you may only examine it for context.
	 * <p>
	 * Note: If <var>source</var> is an instance of {@link Spanned} or
	 * {@link Spannable}, the span objects in the <var>source</var> should be
	 * copied into the filtered result (i.e. the non-null return value).
	 * {@link TextUtils#copySpansFrom} can be used for convenience if the
	 * span boundary indices would be remaining identical relative to the source.
	 *
	 * @param source
	 * @param start
	 * @param end
	 * @param dest
	 * @param dstart
	 * @param dend
	 */
	@Override
	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
		// Code imported from InputFilter.LengthFilter
		// https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/InputFilter.java#175

		// Changes:
		// - After the text it adds and ellipsis to make it feel like the text continues
		// - Trim invisible characters off the end of the already filtered string
		// - Slimmed code for saving LOCs

		int keep = max - (dest.length() - (dend - dstart));
		if(keep <= 0) return "";
		if(keep >= end - start) return null; // keep original

		keep += start;

		while(Character.isWhitespace(source.charAt(keep - 1))) {
			--keep;
			if(keep == start) return "";
		}

		if(Character.isHighSurrogate(source.charAt(keep - 1))) {
			--keep;
			if(keep == start) return "";
		}

		return source.subSequence(start, keep) + "…";
	}
}
