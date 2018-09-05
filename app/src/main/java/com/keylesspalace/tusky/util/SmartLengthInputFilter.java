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

import java.text.BreakIterator;

/**
 * A customized version of {@link android.text.InputFilter.LengthFilter} which allows smarter
 * constraints and adds better visuals such as:
 * <ul>
 *     <li>Ellipsis at the end of the constrained text to show continuation.</li>
 *     <li>Trimming of invisible characters (new lines, spaces, etc.) from the constrained text.</li>
 *     <li>Constraints end at the end of the last "word", before a whitespace.</li>
 *     <li>Expansion of the limit by up to 10 characters to facilitate the previous constraint.</li>
 *     <li>Constraints are not applied if the percentage of hidden content is too small.</li>
 * </ul>
 *
 * Some of these features are configurable through at instancing time.
 */
public class SmartLengthInputFilter implements InputFilter {

	/**
	 * Default for maximum status length on Mastodon and default collapsing
	 * length on Pleroma.
	 */
	public static final int LENGTH_DEFAULT = 50;

	private final int max;
	private final boolean allowRunway;
	private final boolean skipIfBadRatio;

	/**
	 * Creates a new {@link SmartLengthInputFilter} instance with a predefined maximum length and
	 * all the smart constraint features this class supports.
	 *
	 * @param max The maximum length before trimming. May change based on other constraints.
	 */
	public SmartLengthInputFilter(int max) {
		this(max, true, true);
	}

	/**
	 * Fully configures a new {@link SmartLengthInputFilter} to fine tune the state of the
	 * supported smart constraints this class supports.
	 *
	 * @param max            The maximum length before trimming.
	 * @param allowRunway    Whether to extend {@param max} by an extra 10 characters
	 *                       and trim precisely at the end of the closest word.
	 * @param skipIfBadRatio Whether to skip trimming entirely if the trimmed content
	 *                       will be less than 25% of the shown content.
	 */
	public SmartLengthInputFilter(int max, boolean allowRunway, boolean skipIfBadRatio) {
		this.max = max;
		this.allowRunway = allowRunway;
		this.skipIfBadRatio = skipIfBadRatio;
	}

	/**
	 * Calculates if it's worth trimming the message at a specific limit or if the content
	 * that will be hidden will not be enough to justify the operation.
	 *
	 * @param message The message to trim.
	 * @param limit   The maximum length after trimming.
	 * @return        Whether the message should be trimmed or not.
	 */
	public static boolean hasBadRatio(Spanned message, int limit) {
		return (double) limit / message.length() > 0.75;
	}

	/** {@inheritDoc} */
	@Override
	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
		// Code originally imported from InputFilter.LengthFilter but heavily customized.
		// https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/InputFilter.java#175

		int sourceLength = source.length();
		int keep = max - (dest.length() - (dend - dstart));
		if(keep <= 0) return "";
		if(keep >= end - start) return null; // keep original

		keep += start;

		// Enable skipping trimming if the ratio is not good enough
		if(skipIfBadRatio && (double)keep / sourceLength > 0.75)
			return null;

		// Enable trimming at the end of the closest word if possible
		if(allowRunway && Character.isLetterOrDigit(source.charAt(keep))) {
			int boundary;

			// Android N+ offer a clone of the ICU APIs in Java for better internationalization and
			// unicode support. Using the ICU version of BreakIterator grants better support for
			// those without having to add the ICU4J library at a minimum Api trade-off.
			if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
				android.icu.text.BreakIterator iterator = android.icu.text.BreakIterator.getWordInstance();
				iterator.setText(source.toString());
				boundary = iterator.following(keep);
				if(keep - boundary > 10) boundary = iterator.preceding(keep);
			} else {
				java.text.BreakIterator iterator = BreakIterator.getWordInstance();
				iterator.setText(source.toString());
				boundary = iterator.following(keep);
				if(keep - boundary > 10) boundary = iterator.preceding(keep);
			}

			keep = boundary;
		} else {

			// If no runway is allowed simply remove whitespaces if present
			while(Character.isWhitespace(source.charAt(keep - 1))) {
				--keep;
				if(keep == start) return "";
			}
		}

		if(Character.isHighSurrogate(source.charAt(keep - 1))) {
			--keep;
			if(keep == start) return "";
		}

		return source.subSequence(start, keep) + "…";
	}
}
