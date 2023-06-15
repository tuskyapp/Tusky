/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

@file:JvmName("ViewDataUtils")

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
package com.keylesspalace.tusky.util

import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TrendingTag
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.TrendingViewData

fun Status.toViewData(
    isShowingContent: Boolean,
    isExpanded: Boolean,
    isCollapsed: Boolean,
    isDetailed: Boolean = false
): StatusViewData.Concrete {
    return StatusViewData.Concrete(
        status = this,
        isShowingContent = isShowingContent,
        isCollapsed = isCollapsed,
        isExpanded = isExpanded,
        isDetailed = isDetailed
    )
}

fun Notification.toViewData(
    isShowingContent: Boolean,
    isExpanded: Boolean,
    isCollapsed: Boolean
): NotificationViewData {
    return NotificationViewData(
        this.type,
        this.id,
        this.account,
        this.status?.toViewData(isShowingContent, isExpanded, isCollapsed),
        this.report
    )
}

fun List<TrendingTag>.toViewData(): List<TrendingViewData.Tag> {
    val maxTrendingValue = flatMap { tag -> tag.history }
        .mapNotNull { it.uses.toLongOrNull() }
        .maxOrNull() ?: 1

    return map { tag ->

        val reversedHistory = tag.history.asReversed()

        TrendingViewData.Tag(
            name = tag.name,
            usage = reversedHistory.mapNotNull { it.uses.toLongOrNull() },
            accounts = reversedHistory.mapNotNull { it.accounts.toLongOrNull() },
            maxTrendingValue = maxTrendingValue
        )
    }
}
