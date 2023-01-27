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

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowRequestBinding
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.util.visible

class FollowRequestViewHolder(
    private val binding: ItemFollowRequestBinding,
    private val showHeader: Boolean
) : RecyclerView.ViewHolder(binding.root) {

    fun setupWithAccount(
        account: TimelineAccount,
        animateAvatar: Boolean,
        animateEmojis: Boolean,
        showBotOverlay: Boolean
    ) {
        val wrappedName = account.name.unicodeWrap()
        val emojifiedName: CharSequence = wrappedName.emojify(account.emojis, itemView, animateEmojis)
        binding.displayNameTextView.text = emojifiedName
        if (showHeader) {
            val wholeMessage: String = itemView.context.getString(R.string.notification_follow_request_format, wrappedName)
            binding.notificationTextView.text = SpannableStringBuilder(wholeMessage).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, wrappedName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }.emojify(account.emojis, itemView, animateEmojis)
        }
        binding.notificationTextView.visible(showHeader)
        val formattedUsername = itemView.context.getString(R.string.post_username_format, account.username)
        binding.usernameTextView.text = formattedUsername
        val avatarRadius = binding.avatar.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.avatar, avatarRadius, animateAvatar)
        binding.avatarBadge.visible(showBotOverlay && account.bot)
    }

    fun setupActionListener(listener: AccountActionListener, accountId: String) {
        binding.acceptButton.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onRespondToFollowRequest(true, accountId, position)
            }
        }
        binding.rejectButton.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onRespondToFollowRequest(false, accountId, position)
            }
        }
        itemView.setOnClickListener { listener.onViewAccount(accountId) }
    }
}
