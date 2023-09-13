/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.adapter

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.NotificationsAdapter.NotificationActionListener
import com.keylesspalace.tusky.databinding.ItemReportNotificationBinding
import com.keylesspalace.tusky.entity.Report
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getRelativeTimeSpanString
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.unicodeWrap
import java.util.Date

class ReportNotificationViewHolder(
    private val binding: ItemReportNotificationBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun setupWithReport(reporter: TimelineAccount, report: Report, animateAvatar: Boolean, animateEmojis: Boolean) {
        val reporterName = reporter.name.unicodeWrap().emojify(reporter.emojis, itemView, animateEmojis)
        val reporteeName = report.targetAccount.name.unicodeWrap().emojify(report.targetAccount.emojis, itemView, animateEmojis)
        val icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_flag_24dp)

        binding.notificationTopText.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        binding.notificationTopText.text = itemView.context.getString(R.string.notification_header_report_format, reporterName, reporteeName)
        binding.notificationSummary.text = itemView.context.getString(R.string.notification_summary_report_format, getRelativeTimeSpanString(itemView.context, report.createdAt.time, Date().time), report.status_ids?.size ?: 0)
        binding.notificationCategory.text = getTranslatedCategory(itemView.context, report.category)

        // Fancy avatar inset
        val padding = Utils.dpToPx(binding.notificationReporteeAvatar.context, 12)
        binding.notificationReporteeAvatar.setPaddingRelative(0, 0, padding, padding)

        loadAvatar(
            report.targetAccount.avatar,
            binding.notificationReporteeAvatar,
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp),
            animateAvatar,
        )
        loadAvatar(
            reporter.avatar,
            binding.notificationReporterAvatar,
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_24dp),
            animateAvatar,
        )
    }

    fun setupActionListener(listener: NotificationActionListener, reporteeId: String, reporterId: String, reportId: String) {
        binding.notificationReporteeAvatar.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewAccount(reporteeId)
            }
        }
        binding.notificationReporterAvatar.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewAccount(reporterId)
            }
        }

        itemView.setOnClickListener { listener.onViewReport(reportId) }
    }

    private fun getTranslatedCategory(context: Context, rawCategory: String): String {
        return when (rawCategory) {
            "violation" -> context.getString(R.string.report_category_violation)
            "spam" -> context.getString(R.string.report_category_spam)
            "other" -> context.getString(R.string.report_category_other)
            else -> rawCategory
        }
    }
}
