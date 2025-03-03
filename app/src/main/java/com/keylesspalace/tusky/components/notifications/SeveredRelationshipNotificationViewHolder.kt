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

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import com.keylesspalace.tusky.databinding.ItemSeveredRelationshipNotificationBinding
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData

class SeveredRelationshipNotificationViewHolder(
    private val binding: ItemSeveredRelationshipNotificationBinding,
    private val instanceName: String
) : RecyclerView.ViewHolder(binding.root), NotificationsViewHolder {

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        if (payloads.isNotEmpty()) {
            return
        }
        val event = viewData.event!!
        val context = binding.root.context

        binding.severedRelationshipText.text = NotificationService.severedRelationShipText(
            context,
            event,
            instanceName
        )
    }
}
