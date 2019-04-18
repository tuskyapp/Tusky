/* Copyright 2019 Levi Bard
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.adapter

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.bumptech.glide.Glide
import tech.bigfig.roma.R
import tech.bigfig.roma.db.AccountEntity
import tech.bigfig.roma.util.CustomEmojiHelper

import kotlinx.android.synthetic.main.item_autocomplete_account.view.*

class AccountSelectionAdapter(context: Context): ArrayAdapter<AccountEntity>(context, R.layout.item_autocomplete_account) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView

        if (convertView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = layoutInflater.inflate(R.layout.item_autocomplete_account, parent, false)
        }
        view!!

        val account = getItem(position)
        if (account != null) {
            val username = view.username
            val displayName = view.display_name
            val avatar = view.avatar
            username.text = account.fullName
            displayName.text = CustomEmojiHelper.emojifyString(account.displayName, account.emojis, displayName)
            if (!TextUtils.isEmpty(account.profilePictureUrl)) {
                Glide.with(avatar)
                        .load(account.profilePictureUrl)
                        .placeholder(R.drawable.avatar_default)
                        .into(avatar)
            }
        }

        return view
    }
}