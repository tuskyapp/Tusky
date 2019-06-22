package com.keylesspalace.tusky.viewdata

import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PollOption
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