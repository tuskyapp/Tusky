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
package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar

/** Displays a list of blocked accounts. */
class BlocksAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean
) : AccountAdapter<BlocksAdapter.BlockedUserViewHolder>(
    accountActionListener,
    animateAvatar,
    animateEmojis
) {
    override fun createAccountViewHolder(parent: ViewGroup): BlockedUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return BlockedUserViewHolder(view)
    }

    override fun onBindAccountViewHolder(viewHolder: BlockedUserViewHolder, position: Int) {
        viewHolder.setupWithAccount(accountList[position], animateAvatar, animateEmojis)
        viewHolder.setupActionListener(accountActionListener)
    }

    class BlockedUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.blocked_user_avatar)
        private val username: TextView = itemView.findViewById(R.id.blocked_user_username)
        private val displayName: TextView = itemView.findViewById(R.id.blocked_user_display_name)
        private val unblock: ImageButton = itemView.findViewById(R.id.blocked_user_unblock)
        private var id: String? = null

        fun setupWithAccount(account: TimelineAccount, animateAvatar: Boolean, animateEmojis: Boolean) {
            id = account.id
            val emojifiedName = account.name.emojify(account.emojis, displayName, animateEmojis)
            displayName.text = emojifiedName
            val format = username.context.getString(R.string.post_username_format)
            val formattedUsername = String.format(format, account.username)
            username.text = formattedUsername
            val avatarRadius = avatar.context.resources
                .getDimensionPixelSize(R.dimen.avatar_radius_48dp)
            loadAvatar(account.avatar, avatar, avatarRadius, animateAvatar)
        }

        fun setupActionListener(listener: AccountActionListener) {
            unblock.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onBlock(false, id, position)
                }
            }
            itemView.setOnClickListener { listener.onViewAccount(id) }
        }
    }
}
