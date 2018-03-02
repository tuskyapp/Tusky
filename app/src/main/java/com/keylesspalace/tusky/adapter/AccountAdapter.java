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

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.util.ListUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class AccountAdapter extends RecyclerView.Adapter {
    List<Account> accountList;
    AccountActionListener accountActionListener;
    FooterViewHolder.State footerState;

    private String topId;
    private String bottomId;

    AccountAdapter(AccountActionListener accountActionListener) {
        super();
        accountList = new ArrayList<>();
        this.accountActionListener = accountActionListener;
        footerState = FooterViewHolder.State.END;
    }

    @Override
    public int getItemCount() {
        return accountList.size() + 1;
    }

    public void update(@Nullable List<Account> newAccounts, @Nullable String fromId,
                       @Nullable String uptoId) {
        if (newAccounts == null || newAccounts.isEmpty()) {
            return;
        }

        bottomId = fromId;
        topId = uptoId;

        if (accountList.isEmpty()) {
            accountList = ListUtils.removeDuplicates(newAccounts);
        } else {
            int index = accountList.indexOf(newAccounts.get(newAccounts.size() - 1));
            for (int i = 0; i < index; i++) {
                accountList.remove(0);
            }
            int newIndex = newAccounts.indexOf(accountList.get(0));
            if (newIndex == -1) {
                accountList.addAll(0, newAccounts);
            } else {
                accountList.addAll(0, newAccounts.subList(0, newIndex));
            }
        }
        notifyDataSetChanged();
    }

    public void addItems(List<Account> newAccounts, @Nullable String fromId) {
        if (fromId != null) {
            bottomId = fromId;
        }
        int end = accountList.size();
        Account last = accountList.get(end - 1);
        if (last != null && !findAccount(newAccounts, last.id)) {
            accountList.addAll(newAccounts);
            notifyItemRangeInserted(end, newAccounts.size());
        }
    }

    private static boolean findAccount(List<Account> accounts, String id) {
        for (Account account : accounts) {
            if (account.id.equals(id)) {
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

    @Nullable
    public Account getItem(int position) {
        if (position >= 0 && position < accountList.size()) {
            return accountList.get(position);
        }
        return null;
    }

    public void setFooterState(FooterViewHolder.State newFooterState) {
        footerState = newFooterState;
    }

    @Nullable
    public String getBottomId() {
        return bottomId;
    }

    @Nullable
    public String getTopId() {
        return topId;
    }
}
