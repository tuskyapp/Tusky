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

import android.content.Context;

import com.keylesspalace.tusky.R;

public class DateUtils {

    private static final long SECOND_IN_MILLIS = 1000;
    private static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
    private static final long HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60;
    private static final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
    private static final long YEAR_IN_MILLIS = DAY_IN_MILLIS * 365;

    /**
     * This is a rough duplicate of {@link android.text.format.DateUtils#getRelativeTimeSpanString},
     * but even with the FORMAT_ABBREV_RELATIVE flag it wasn't abbreviating enough.
     */
    public static String getRelativeTimeSpanString(Context context, long then, long now) {
        long span = now - then;
        boolean future = false;
        if (span < 0) {
            future = true;
            span = -span;
        }
        int format;
        if (span < MINUTE_IN_MILLIS) {
            span /= SECOND_IN_MILLIS;
            if (future) {
                format = R.string.abbreviated_in_seconds;
            } else {
                format = R.string.abbreviated_seconds_ago;
            }
        } else if (span < HOUR_IN_MILLIS) {
            span /= MINUTE_IN_MILLIS;
            if (future) {
                format = R.string.abbreviated_in_minutes;
            } else {
                format = R.string.abbreviated_minutes_ago;
            }
        } else if (span < DAY_IN_MILLIS) {
            span /= HOUR_IN_MILLIS;
            if (future) {
                format = R.string.abbreviated_in_hours;
            } else {
                format = R.string.abbreviated_hours_ago;
            }
        } else if (span < YEAR_IN_MILLIS) {
            span /= DAY_IN_MILLIS;
            if (future) {
                format = R.string.abbreviated_in_days;
            } else {
                format = R.string.abbreviated_days_ago;
            }
        } else {
            span /= YEAR_IN_MILLIS;
            if (future) {
                format = R.string.abbreviated_in_years;
            } else {
                format = R.string.abbreviated_years_ago;
            }
        }
        return context.getString(format, span);
    }

    public static String formatPollDuration(Context context, long then, long now) {
        long span = then - now;
        if (span < 0) {
            span = 0;
        }
        int format;
        if (span < MINUTE_IN_MILLIS) {
            span /= SECOND_IN_MILLIS;
            format = R.plurals.poll_timespan_seconds;
        } else if (span < HOUR_IN_MILLIS) {
            span /= MINUTE_IN_MILLIS;
            format = R.plurals.poll_timespan_minutes;

        } else if (span < DAY_IN_MILLIS) {
            span /= HOUR_IN_MILLIS;
            format = R.plurals.poll_timespan_hours;

        } else {
            span /= DAY_IN_MILLIS;
            format = R.plurals.poll_timespan_days;
        }
        return context.getResources().getQuantityString(format, (int) span, (int) span);
    }

}
