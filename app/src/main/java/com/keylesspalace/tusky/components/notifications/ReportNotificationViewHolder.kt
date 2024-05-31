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

package com.keylesspalace.tusky.components.notifications

import android.content.Context
import android.text.TextUtils
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemReportNotificationBinding
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.getRelativeTimeSpanString
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.util.updateEmojiTargets
import com.keylesspalace.tusky.viewdata.NotificationViewData

class ReportNotificationViewHolder(
    private val binding: ItemReportNotificationBinding,
    private val listener: NotificationActionListener,
    private val accountActionListener: AccountActionListener
) : RecyclerView.ViewHolder(binding.root), NotificationsViewHolder {

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val report = viewData.report!!
        val reporter = viewData.account

        binding.notificationTopText.updateEmojiTargets {
            val reporterName = reporter.name.unicodeWrap().emojify(reporter.emojis, statusDisplayOptions.animateEmojis)
            val reporteeName = report.targetAccount.name.unicodeWrap().emojify(report.targetAccount.emojis, statusDisplayOptions.animateEmojis)

            // Context.getString() returns a String and doesn't support Spannable.
            // Convert the placeholders to the format used by TextUtils.expandTemplate which does.
            val topText =
                view.context.getString(R.string.notification_header_report_format, "^1", "^2")
            view.text = TextUtils.expandTemplate(topText, reporterName, reporteeName)
        }
        binding.notificationSummary.text = itemView.context.getString(R.string.notification_summary_report_format, getRelativeTimeSpanString(itemView.context, report.createdAt.time, System.currentTimeMillis()), report.statusIds?.size ?: 0)
        binding.notificationCategory.text = getTranslatedCategory(itemView.context, report.category)

        loadAvatar(
            report.targetAccount.avatar,
            binding.notificationReporteeAvatar,
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp),
            statusDisplayOptions.animateAvatars,
        )
        loadAvatar(
            reporter.avatar,
            binding.notificationReporterAvatar,
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_24dp),
            statusDisplayOptions.animateAvatars,
        )

        binding.notificationReporteeAvatar.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                accountActionListener.onViewAccount(report.targetAccount.id)
            }
        }
        binding.notificationReporterAvatar.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                accountActionListener.onViewAccount(reporter.id)
            }
        }

        itemView.setOnClickListener { listener.onViewReport(report.id) }
    }

    private fun getTranslatedCategory(context: Context, rawCategory: String): String {
        return when (rawCategory) {
            "violation" -> context.getString(R.string.report_category_violation)
            "spam" -> context.getString(R.string.report_category_spam)
            "legal" -> context.getString(R.string.report_category_legal)
            "other" -> context.getString(R.string.report_category_other)
            else -> rawCategory
        }
    }
}
