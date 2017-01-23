/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

public class DateUtils {
    /* This is a rough duplicate of android.text.format.DateUtils.getRelativeTimeSpanString,
     * but even with the FORMAT_ABBREV_RELATIVE flag it wasn't abbreviating enough. */
    public static String getRelativeTimeSpanString(long then, long now) {
        final long MINUTE = 60;
        final long HOUR = 60 * MINUTE;
        final long DAY = 24 * HOUR;
        final long YEAR = 365 * DAY;
        long span = (now - then) / 1000;
        String prefix = "";
        if (span < 0) {
            prefix = "in ";
            span = -span;
        }
        String unit;
        if (span < MINUTE) {
            unit = "s";
        } else if (span < HOUR) {
            span /= MINUTE;
            unit = "m";
        } else if (span < DAY) {
            span /= HOUR;
            unit = "h";
        } else if (span < YEAR) {
            span /= DAY;
            unit = "d";
        } else {
            span /= YEAR;
            unit = "y";
        }
        return prefix + span + unit;
    }
}
