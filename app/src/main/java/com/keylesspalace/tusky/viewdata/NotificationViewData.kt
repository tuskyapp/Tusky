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

import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.entity.TimelineAccount

sealed class NotificationViewData {

    abstract val id: String

    class Concrete(
        override val id: String,
        val type: Notification.Type,
        val account: TimelineAccount,
        val statusViewData: StatusViewData.Concrete?,
        val report: Report?
    ) : NotificationViewData()

    class Placeholder(
        override val id: String,
        val isLoading: Boolean
    ) : NotificationViewData()
}
