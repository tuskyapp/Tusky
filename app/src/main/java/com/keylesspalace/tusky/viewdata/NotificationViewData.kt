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
package com.keylesspalace.tusky.viewdata

import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.NotificationViewData.Concrete
import com.keylesspalace.tusky.viewdata.NotificationViewData.Placeholder
import java.util.Objects

/**
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a [Placeholder] or a [Concrete].
 * It is modelled this way because close relationship between placeholder and concrete notification
 * is fine in this case. Placeholder case is not modelled as a type of notification because
 * invariants would be violated and because it would model domain incorrectly. It is preferable to
 * [com.keylesspalace.tusky.util.Either] because class hierarchy is cheaper, faster and
 * more native.
 */
abstract class NotificationViewData {
    abstract val viewDataId: Long

    abstract fun deepEquals(other: NotificationViewData?): Boolean

    data class Concrete(
        val type: Notification.Type,
        val id: String,
        val account: TimelineAccount,
        var statusViewData: StatusViewData.Concrete?,
        val report: Report?
    ) : NotificationViewData() {
        override val viewDataId: Long get() = id.hashCode().toLong()

        override fun hashCode(): Int {
            return Objects.hash(type, id, account, statusViewData, report)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Concrete

            if (type != other.type) return false
            if (id != other.id) return false
            if (account != other.account) return false
            if (statusViewData != other.statusViewData) return false
            if (report != other.report) return false

            return true
        }

        override fun deepEquals(other: NotificationViewData?): Boolean {
            if (this == other) return true
            if (other == null || javaClass != other.javaClass) return false
            val concrete = other as Concrete
            return type === concrete.type &&
                id == concrete.id &&
                account.id == concrete.account.id &&
                statusViewData == concrete.statusViewData &&
                report == concrete.report
        }
    }
}
