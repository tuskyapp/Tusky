/* Copyright 2019 Levi Bard
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemAutocompleteAccountBinding
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.settings.PrefStore
import com.keylesspalace.tusky.settings.getBlocking
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar

class AccountSelectionAdapter(
    context: Context,
    private val prefStore: PrefStore,
) : ArrayAdapter<AccountEntity>(context, R.layout.item_autocomplete_account) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemAutocompleteAccountBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemAutocompleteAccountBinding.bind(convertView)
        }

        val account = getItem(position)
        if (account != null) {
            // TODO: is this even okay to do prefs things for each invocation here?
            val prefs = prefStore.getBlocking()
            val animateEmojis = prefs.animateEmojis

            binding.username.text = account.fullName
            binding.displayName.text =
                account.displayName.emojify(account.emojis, binding.displayName, animateEmojis)

            val avatarRadius = context.resources.getDimensionPixelSize(R.dimen.avatar_radius_42dp)
            val animateAvatar = prefs.animateAvatars

            loadAvatar(account.profilePictureUrl, binding.avatar, avatarRadius, animateAvatar)
        }

        return binding.root
    }
}
