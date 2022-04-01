/* Copyright 2022 Tusky Contributors
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

public class AbsoluteTimeFormatter {
    private val sameDaySdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val sameYearSdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val otherYearSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val otherYearCompleteSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun format(time: Date?, shortFormat: Boolean = true): String {
        return if (time == null) {
            "??"
        } else if (android.text.format.DateUtils.isToday(time.time)) {
            sameDaySdf.format(time)
        } else if (AbsoluteTimeFormatter.isThisYear(time)) {
            sameYearSdf.format(time)
        } else if (shortFormat) {
            otherYearSdf.format(time)
        } else {
            otherYearCompleteSdf.format(time)
        }
    }

    companion object {
        private fun isThisYear(time: Date): Boolean {
            val dateYear = time.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().getYear()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            return dateYear == currentYear
        }
    }
}

@RunWith(AndroidJUnit4::class)
class AbsoluteTimeFormatterTest {
    private val absoluteTimeFormatter: AbsoluteTimeFormatter = AbsoluteTimeFormatter(Locale.US)

    @Test
    fun shouldFormatWithInterrogationPoints() {
        assertTrue(absoluteTimeFormatter.format(null).equals("??"))
        assertTrue(absoluteTimeFormatter.format(null, false).equals("??"))
    }
}
