/* Copyright 2023 Tusky Contributors
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

package com.keylesspalace.tusky.viewdata

import java.util.Date

sealed interface TrendingViewData {
    val id: String

    data class Header(
        val start: Date,
        val end: Date
    ) : TrendingViewData {
        override val id: String = start.toString() + end.toString()
    }

    data class Tag(
        val name: String,
        val usage: List<Long>,
        val accounts: List<Long>,
        val maxTrendingValue: Long
    ) : TrendingViewData {
        override val id: String = name
    }
}
