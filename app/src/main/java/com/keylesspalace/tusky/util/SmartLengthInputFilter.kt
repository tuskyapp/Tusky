/* Copyright 2019 Tusky contributors
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

package com.keylesspalace.tusky.util

import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned

/**
 * Defines how many characters to extend beyond the limit to cut at the end of the word on the
 * boundary of it rather than cutting at the word preceding that one.
 */
private const val RUNWAY = 10

/**
 * Default for maximum status length on Mastodon and default collapsing length on Pleroma.
 */
private const val LENGTH_DEFAULT = 500

/**
 * Calculates if it's worth trimming the message at a specific limit or if the content that will
 * be hidden will not be enough to justify the operation.
 *
 * @param message The message to trim.
 * @return        Whether the message should be trimmed or not.
 */
fun shouldTrimStatus(message: Spanned): Boolean {
	return message.isNotEmpty() && LENGTH_DEFAULT.toFloat() / message.length < 0.75
}

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
 */
object SmartLengthInputFilter : InputFilter {
	/** {@inheritDoc} */
	override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
		// Code originally imported from InputFilter.LengthFilter but heavily customized and converted to Kotlin.
		// https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/InputFilter.java#175

		val sourceLength = source.length
		var keep = LENGTH_DEFAULT - (dest.length - (dend - dstart))
		if (keep <= 0) return ""
		if (keep >= end - start) return null // Keep original

		keep += start

		// Skip trimming if the ratio doesn't warrant it
		if (keep.toDouble() / sourceLength > 0.75) return null

		// Enable trimming at the end of the closest word if possible
		if (source[keep].isLetterOrDigit()) {
			var boundary: Int

			// Android N+ offer a clone of the ICU APIs in Java for better internationalization and
			// unicode support. Using the ICU version of BreakIterator grants better support for
			// those without having to add the ICU4J library at a minimum Api trade-off.
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
				val iterator = android.icu.text.BreakIterator.getWordInstance()
			iterator.setText(source.toString())
			boundary = iterator.following(keep)
			if (keep - boundary > RUNWAY) boundary = iterator.preceding(keep)
		} else {
			val iterator = java.text.BreakIterator.getWordInstance()
			iterator.setText(source.toString())
			boundary = iterator.following(keep)
			if (keep - boundary > RUNWAY) boundary = iterator.preceding(keep)
		}

		keep = boundary
		} else {

			// If no runway is allowed simply remove whitespaces if present
			while(source[keep - 1].isWhitespace()) {
				--keep
				if (keep == start) return ""
			}
		}

		if (source[keep - 1].isHighSurrogate()) {
			--keep
			if (keep == start) return ""
		}

		return if (source is Spanned) {
			SpannableStringBuilder(source, start, keep).append("…")
		} else {
			"${source.subSequence(start, keep)}…"
		}
	}
}