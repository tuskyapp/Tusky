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
 * Created by charlag on 12/07/2017.
 *
 *
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a [Placeholder] or a [Concrete].
 * It is modelled this way because close relationship between placeholder and concrete notification
 * is fine in this case. Placeholder case is not modelled as a type of notification because
 * invariants would be violated and because it would model domain incorrectly. It is preferable to
 * [com.keylesspalace.tusky.util.Either] because class hierarchy is cheaper, faster and
 * more native.
 */
abstract class NotificationViewData private constructor() {
    abstract val viewDataId: Long

    abstract fun deepEquals(other: NotificationViewData?): Boolean

    class Concrete(
        val type: Notification.Type,
        val id: String,
        val account: TimelineAccount,
        val statusViewData: StatusViewData.Concrete?,
        val report: Report?
    ) : NotificationViewData() {

        override val viewDataId: Long = id.hashCode().toLong()

        override fun deepEquals(other: NotificationViewData?): Boolean {
            if (this == other) return true
            if (other == null || javaClass != other.javaClass) return false
            val concrete = other as Concrete
            return type == concrete.type && id == concrete.id && account.id == concrete.account.id &&
                (statusViewData == concrete.statusViewData) &&
                (report == concrete.report)
        }

        override fun hashCode(): Int {
            return Objects.hash(type, id, account, statusViewData)
        }

        fun copyWithStatus(statusViewData: StatusViewData.Concrete?): Concrete {
            return Concrete(type, id, account, statusViewData, report)
        }
    }

    class Placeholder(
        private val id: Long,
        val isLoading: Boolean
    ) : NotificationViewData() {
        override val viewDataId: Long = id

        override fun deepEquals(other: NotificationViewData?): Boolean {
            if (other !is Placeholder) return false
            return isLoading == other.isLoading && id == other.id
        }
    }
}
