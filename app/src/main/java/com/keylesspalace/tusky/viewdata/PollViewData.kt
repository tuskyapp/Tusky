/* Copyright 2019 Conny Duck
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

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PollOption
import com.keylesspalace.tusky.util.HtmlUtils
import java.util.*
import kotlin.math.roundToInt

data class PollViewData(
        val id: String,
        val expiresAt: Date?,
        val expired: Boolean,
        val multiple: Boolean,
        val votesCount: Int,
        val options: List<PollOptionViewData>,
        var voted: Boolean
)

data class PollOptionViewData(
        val title: String,
        var votesCount: Int,
        var selected: Boolean
)

fun calculatePercent(fraction: Int, total: Int): Int {
    return if (fraction == 0) {
        0
    } else {
        (fraction / total.toDouble() * 100).roundToInt()
    }
}

fun buildDescription(title: String, percent: Int, context: Context): Spanned {
    return SpannableStringBuilder(HtmlUtils.fromHtml(context.getString(R.string.poll_percent_format, percent)))
        .append(" ")
        .append(title)
}

fun Poll?.toViewData(): PollViewData? {
    if (this == null) return null
    return PollViewData(
            id,
            expiresAt,
            expired,
            multiple,
            votesCount,
            options.map { it.toViewData() },
            voted
    )
}

fun PollOption.toViewData(): PollOptionViewData {
    return PollOptionViewData(
            title,
            votesCount,
            false
    )
}