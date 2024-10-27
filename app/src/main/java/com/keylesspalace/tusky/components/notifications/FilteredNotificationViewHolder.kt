/* Copyright 2024 Tusky Contributors
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

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.text.toSpannable
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemNotificationFilteredBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.NotificationViewData

class FilteredNotificationViewHolder(
    private val binding: ItemNotificationFilteredBinding,
    private val accountActionListener: AccountActionListener,
    private val notificationActionListener: NotificationActionListener
) : NotificationsViewHolder, RecyclerView.ViewHolder(binding.root) {

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val displayName = viewData.account.name.unicodeWrap()

        val wholeMessage = String.format("Filtered notification from %1\$s", displayName).toSpannable()
        val displayNameIndex = "Filtered notification from %1\$s".indexOf("%1\$s")
        wholeMessage.setSpan(
            StyleSpan(Typeface.BOLD),
            displayNameIndex,
            displayNameIndex + displayName.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val emojifiedText = wholeMessage.emojify(
            viewData.account.emojis,
            binding.notificationTopText,
            statusDisplayOptions.animateEmojis
        )
        binding.notificationTopText.text = emojifiedText

        binding.notificationTopText.setOnClickListener {
            accountActionListener.onViewAccount(viewData.account.id)
        }

        binding.buttonAccept.setOnClickListener {
            notificationActionListener.onAcceptNotificationRequest(viewData.id)
        }
        binding.buttonDeny.setOnClickListener {
            notificationActionListener.onDismissNotificationRequest(viewData.id)
        }
    }
}
