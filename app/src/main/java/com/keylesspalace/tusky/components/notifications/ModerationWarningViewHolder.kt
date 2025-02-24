/* Copyright 2025 Tusky Contributors
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

import android.content.Intent
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.databinding.ItemModerationWarningNotificationBinding
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData

class ModerationWarningViewHolder(
    private val binding: ItemModerationWarningNotificationBinding,
    private val instanceDomain: String
) : RecyclerView.ViewHolder(binding.root), NotificationsViewHolder {

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        if (payloads.isNotEmpty()) {
            return
        }
        val warning = viewData.moderationWarning!!

        binding.moderationWarningDescription.setText(warning.action.text)

        binding.root.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://$instanceDomain/disputes/strikes/${warning.id}".toUri())
            binding.root.context.startActivity(intent)
        }
    }
}
