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
import com.keylesspalace.tusky.adapter.AccountViewHolder
import com.keylesspalace.tusky.databinding.ItemAccountBinding
import com.keylesspalace.tusky.interfaces.AccountActionListener

/** Displays either a follows or following list.  */
class FollowAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean,
    showBotOverlay: Boolean
) : AccountAdapter<AccountViewHolder>(
    accountActionListener = accountActionListener,
    animateAvatar = animateAvatar,
    animateEmojis = animateEmojis,
    showBotOverlay = showBotOverlay
) {

    override fun createAccountViewHolder(parent: ViewGroup): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccountViewHolder(binding)
    }

    override fun onBindAccountViewHolder(viewHolder: AccountViewHolder, position: Int) {
        viewHolder.setupWithAccount(
            accountList[position],
            animateAvatar,
            animateEmojis,
            showBotOverlay
        )
        viewHolder.setupActionListener(accountActionListener)
    }
}
