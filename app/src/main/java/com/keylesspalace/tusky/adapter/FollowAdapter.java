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

package com.keylesspalace.tusky.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.AccountActionListener;

/** Both for follows and following lists. */
public class FollowAdapter extends AccountAdapter {
    private static final int VIEW_TYPE_ACCOUNT = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    public FollowAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_ACCOUNT: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_account, parent, false);
                return new AccountViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_footer, parent, false);
                return new FooterViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < accountList.size()) {
            AccountViewHolder holder = (AccountViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
            holder.setupActionListener(accountActionListener);
        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(footerState);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == accountList.size()) {
            return VIEW_TYPE_FOOTER;
        } else {
            return VIEW_TYPE_ACCOUNT;
        }
    }
}
