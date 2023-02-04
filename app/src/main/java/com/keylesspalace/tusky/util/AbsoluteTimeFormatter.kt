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

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AbsoluteTimeFormatter @JvmOverloads constructor(private val tz: TimeZone = TimeZone.getDefault(), is24HourFormat: Boolean) {
    private val sameDaySkeleton = if (is24HourFormat) "HH:mm" else "hh:mm a"
    private val sameYearSkeleton = if (is24HourFormat) "dd MMM, HH:mm" else "dd MMM, hh:mm a"
    private val otherYearSkeleton = "yyyy-MM-dd"
    private val otherYearCompleteSkeleton = if (is24HourFormat) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd hh:mm a"

    // DateFormat.getBestDateTimePattern() will return null in unit test.
    private val sameDayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), sameDaySkeleton) ?: sameDaySkeleton
    private val sameYearPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), sameYearSkeleton) ?: sameYearSkeleton
    private val otherYearPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), otherYearSkeleton) ?: otherYearSkeleton
    private val otherYearCompletePattern =
        DateFormat.getBestDateTimePattern(Locale.getDefault(), otherYearCompleteSkeleton) ?: otherYearCompleteSkeleton

    private val sameDaySdf = SimpleDateFormat(sameDayPattern, Locale.getDefault()).apply { this.timeZone = tz }
    private val sameYearSdf = SimpleDateFormat(sameYearPattern, Locale.getDefault()).apply { this.timeZone = tz }
    private val otherYearSdf = SimpleDateFormat(otherYearPattern, Locale.getDefault()).apply { this.timeZone = tz }
    private val otherYearCompleteSdf = SimpleDateFormat(otherYearCompletePattern, Locale.getDefault()).apply { this.timeZone = tz }

    @JvmOverloads
    fun format(time: Date?, shortFormat: Boolean = true, now: Date = Date()): String {
        return when {
            time == null -> "??"
            isSameDate(time, now, tz) -> sameDaySdf.format(time)
            isSameYear(time, now, tz) -> sameYearSdf.format(time)
            shortFormat -> otherYearSdf.format(time)
            else -> otherYearCompleteSdf.format(time)
        }
    }

    companion object {

        private fun isSameDate(dateOne: Date, dateTwo: Date, tz: TimeZone): Boolean {
            val calendarOne = Calendar.getInstance(tz).apply { time = dateOne }
            val calendarTwo = Calendar.getInstance(tz).apply { time = dateTwo }

            return calendarOne.get(Calendar.YEAR) == calendarTwo.get(Calendar.YEAR) &&
                calendarOne.get(Calendar.MONTH) == calendarTwo.get(Calendar.MONTH) &&
                calendarOne.get(Calendar.DAY_OF_MONTH) == calendarTwo.get(Calendar.DAY_OF_MONTH)
        }

        private fun isSameYear(dateOne: Date, dateTwo: Date, timeZone1: TimeZone): Boolean {
            val calendarOne = Calendar.getInstance(timeZone1).apply { time = dateOne }
            val calendarTwo = Calendar.getInstance(timeZone1).apply { time = dateTwo }

            return calendarOne.get(Calendar.YEAR) == calendarTwo.get(Calendar.YEAR)
        }
    }
}
