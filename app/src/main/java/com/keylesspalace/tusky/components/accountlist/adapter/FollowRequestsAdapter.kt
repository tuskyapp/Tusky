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

package com.keylesspalace.tusky.components.accountlist.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import com.keylesspalace.tusky.adapter.FollowRequestViewHolder
import com.keylesspalace.tusky.databinding.ItemFollowRequestBinding
import com.keylesspalace.tusky.interfaces.AccountActionListener

/** Displays a list of follow requests with accept/reject buttons. */
class FollowRequestsAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean,
    showBotOverlay: Boolean
) : AccountAdapter<FollowRequestViewHolder>(
    accountActionListener = accountActionListener,
    animateAvatar = animateAvatar,
    animateEmojis = animateEmojis,
    showBotOverlay = showBotOverlay
) {

    override fun createAccountViewHolder(parent: ViewGroup): FollowRequestViewHolder {
        val binding = ItemFollowRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FollowRequestViewHolder(binding, false)
    }

    override fun onBindAccountViewHolder(viewHolder: FollowRequestViewHolder, position: Int) {
        viewHolder.setupWithAccount(
            account = accountList[position],
            animateAvatar = animateAvatar,
            animateEmojis = animateEmojis,
            showBotOverlay = showBotOverlay
        )
        viewHolder.setupActionListener(accountActionListener, accountList[position].id)
    }
}
