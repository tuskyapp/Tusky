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
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.SearchResults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_ACCOUNT = 0;
    private static final int VIEW_TYPE_HASHTAG = 1;

    private List<Account> accountList;
    private List<String> hashtagList;

    public SearchResultsAdapter() {
        super();
        accountList = new ArrayList<>();
        hashtagList = new ArrayList<>();
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
            case VIEW_TYPE_HASHTAG: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_hashtag, parent, false);
                return new HashtagViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < accountList.size()) {
            AccountViewHolder holder = (AccountViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
        } else {
            HashtagViewHolder holder = (HashtagViewHolder) viewHolder;
            int index = position - accountList.size();
            holder.setHashtag(hashtagList.get(index));
        }
    }

    @Override
    public int getItemCount() {
        return accountList.size() + hashtagList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= accountList.size()) {
            return VIEW_TYPE_HASHTAG;
        } else {
            return VIEW_TYPE_ACCOUNT;
        }
    }

    public void updateSearchResults(SearchResults results) {
        if (results != null) {
            if (results.accounts != null) {
                accountList.addAll(Arrays.asList(results.accounts));
            }
            if (results.hashtags != null) {
                hashtagList.addAll(Arrays.asList(results.hashtags));
            }
        } else {
            accountList.clear();
            hashtagList.clear();
        }
        notifyDataSetChanged();
    }

    private static class HashtagViewHolder extends RecyclerView.ViewHolder {
        private TextView hashtag;

        HashtagViewHolder(View itemView) {
            super(itemView);
            hashtag = (TextView) itemView.findViewById(R.id.hashtag);
        }

        void setHashtag(String tag) {
            hashtag.setText(String.format("#%s", tag));
        }
    }
}
