/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

abstract class AccountAdapter extends RecyclerView.Adapter {
    List<Account> accountList;
    AccountActionListener accountActionListener;
    FooterActionListener footerActionListener;
    FooterViewHolder.State footerState;

    AccountAdapter(AccountActionListener accountActionListener,
            FooterActionListener footerActionListener) {
        super();
        accountList = new ArrayList<>();
        this.accountActionListener = accountActionListener;
        this.footerActionListener = footerActionListener;
        footerState = FooterViewHolder.State.LOADING;
    }

    @Override
    public int getItemCount() {
        return accountList.size() + 1;
    }

    void update(List<Account> newAccounts) {
        if (accountList == null || accountList.isEmpty()) {
            accountList = newAccounts;
        } else {
            int index = newAccounts.indexOf(accountList.get(0));
            if (index == -1) {
                accountList.addAll(0, newAccounts);
            } else {
                accountList.addAll(0, newAccounts.subList(0, index));
            }
        }
        notifyDataSetChanged();
    }

    void addItems(List<Account> newAccounts) {
        int end = accountList.size();
        accountList.addAll(newAccounts);
        notifyItemRangeInserted(end, newAccounts.size());
    }

    public Account getItem(int position) {
        if (position >= 0 && position < accountList.size()) {
            return accountList.get(position);
        }
        return null;
    }

    void setFooterState(FooterViewHolder.State state) {
        this.footerState = state;
    }
}
