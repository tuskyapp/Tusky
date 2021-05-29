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
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.toViewData
import java.util.*

/**
 * Created by charlag on 12/07/2017.
 */
object ViewDataUtils {
    @JvmStatic
    fun statusToViewData(
        status: Status,
        alwaysShowSensitiveMedia: Boolean,
        alwaysOpenSpoiler: Boolean
    ): StatusViewData.Concrete {
        val visibleStatus = status.reblog ?: status

        return StatusViewData.Concrete(
            status = status,
            isShowingContent = alwaysShowSensitiveMedia || !visibleStatus.sensitive,
            isCollapsible = shouldTrimStatus(visibleStatus.content),
            isCollapsed = false,
            isExpanded = alwaysOpenSpoiler,
        )
    }

    @JvmStatic
    fun notificationToViewData(
        notification: Notification,
        alwaysShowSensitiveData: Boolean,
        alwaysOpenSpoiler: Boolean
    ): NotificationViewData.Concrete {
        return NotificationViewData.Concrete(
            notification.type,
            notification.id,
            notification.account,
            notification.status?.let { status ->
                statusToViewData(status, alwaysShowSensitiveData, alwaysOpenSpoiler)
            }
        )
    }
}