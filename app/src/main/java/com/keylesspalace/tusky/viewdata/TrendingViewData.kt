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

import com.keylesspalace.tusky.entity.TrendingTag
import com.keylesspalace.tusky.entity.end
import com.keylesspalace.tusky.entity.start
import java.util.Date

sealed class TrendingViewData {
    abstract val id: String

    data class Header(
        val start: Date,
        val end: Date,
    ) : TrendingViewData() {
        override val id: String
            get() = start.toString() + end.toString()
    }

    fun asHeaderOrNull(): Header? {
        val tag = (this as? Tag)?.tag
            ?: return null
        return Header(tag.start(), tag.end())
    }

    data class Tag(
        val tag: TrendingTag
    ) : TrendingViewData() {
        override val id: String
            get() = tag.name
    }

    fun asTagOrNull() = this as? Tag
}
