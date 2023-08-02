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

package com.keylesspalace.tusky.components.notifications

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowBinding
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.NotificationViewData

class FollowViewHolder(
    private val binding: ItemFollowBinding,
    private val notificationActionListener: NotificationActionListener,
    private val linkListener: LinkListener
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius42dp = itemView.context.resources.getDimensionPixelSize(
        R.dimen.avatar_radius_42dp
    )

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        // Skip updates with payloads. That indicates a timestamp update, and
        // this view does not have timestamps.
        if (!payloads.isNullOrEmpty()) return

        setMessage(
            viewData.account,
            viewData.type === Notification.Type.SIGN_UP,
            statusDisplayOptions.animateAvatars,
            statusDisplayOptions.animateEmojis
        )
        setupButtons(notificationActionListener, viewData.account.id)
    }

    private fun setMessage(
        account: TimelineAccount,
        isSignUp: Boolean,
        animateAvatars: Boolean,
        animateEmojis: Boolean
    ) {
        val context = binding.notificationText.context
        val format =
            context.getString(
                if (isSignUp) {
                    R.string.notification_sign_up_format
                } else {
                    R.string.notification_follow_format
                }
            )
        val wrappedDisplayName = account.name.unicodeWrap()
        val wholeMessage = String.format(format, wrappedDisplayName)
        val emojifiedMessage =
            wholeMessage.emojify(
                account.emojis,
                binding.notificationText,
                animateEmojis
            )
        binding.notificationText.text = emojifiedMessage
        val username = context.getString(R.string.post_username_format, account.username)
        binding.notificationUsername.text = username
        val emojifiedDisplayName = wrappedDisplayName.emojify(
            account.emojis,
            binding.notificationUsername,
            animateEmojis
        )
        binding.notificationDisplayName.text = emojifiedDisplayName
        loadAvatar(
            account.avatar,
            binding.notificationAvatar,
            avatarRadius42dp,
            animateAvatars
        )

        val emojifiedNote = account.note.parseAsMastodonHtml().emojify(
            account.emojis,
            binding.notificationAccountNote,
            animateEmojis
        )
        setClickableText(binding.notificationAccountNote, emojifiedNote, emptyList(), null, linkListener)
    }

    private fun setupButtons(listener: NotificationActionListener, accountId: String) {
        binding.root.setOnClickListener { listener.onViewAccount(accountId) }
    }
}
