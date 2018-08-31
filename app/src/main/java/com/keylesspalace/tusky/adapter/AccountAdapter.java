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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.util.ListUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class AccountAdapter extends RecyclerView.Adapter {
    static final int VIEW_TYPE_ACCOUNT = 0;
    static final int VIEW_TYPE_FOOTER = 1;


    List<Account> accountList;
    AccountActionListener accountActionListener;
    private boolean bottomLoading;

    AccountAdapter(AccountActionListener accountActionListener) {
        this.accountList = new ArrayList<>();
        this.accountActionListener = accountActionListener;
        bottomLoading = false;
    }

    @Override
    public int getItemCount() {
        return accountList.size() + (bottomLoading ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == accountList.size() && bottomLoading) {
            return VIEW_TYPE_FOOTER;
        } else {
            return VIEW_TYPE_ACCOUNT;
        }
    }

    public void update(@NonNull List<Account> newAccounts) {
        accountList = ListUtils.removeDuplicates(newAccounts);
        notifyDataSetChanged();
    }

    public void addItems(List<Account> newAccounts) {
        int end = accountList.size();
        Account last = accountList.get(end - 1);
        if (last != null && !findAccount(newAccounts, last.getId())) {
            accountList.addAll(newAccounts);
            notifyItemRangeInserted(end, newAccounts.size());
        }
    }

    public void setBottomLoading(boolean loading) {
        boolean wasLoading = bottomLoading;
        if(wasLoading == loading) {
            return;
        }
        bottomLoading = loading;
        if(loading) {
            notifyItemInserted(accountList.size());
        } else {
            notifyItemRemoved(accountList.size());
        }
    }

    private static boolean findAccount(List<Account> accounts, String id) {
        for (Account account : accounts) {
            if (account.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Account removeItem(int position) {
        if (position < 0 || position >= accountList.size()) {
            return null;
        }
        Account account = accountList.remove(position);
        notifyItemRemoved(position);
        return account;
    }

    public void addItem(Account account, int position) {
        if (position < 0 || position > accountList.size()) {
            return;
        }
        accountList.add(position, account);
        notifyItemInserted(position);
    }


}
