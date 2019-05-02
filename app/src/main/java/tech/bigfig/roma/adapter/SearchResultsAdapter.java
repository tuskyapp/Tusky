/* Copyright 2017 Andrew Dawson
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

package tech.bigfig.roma.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import tech.bigfig.roma.R;
import tech.bigfig.roma.entity.Account;
import tech.bigfig.roma.entity.SearchResults;
import tech.bigfig.roma.entity.Status;
import tech.bigfig.roma.interfaces.LinkListener;
import tech.bigfig.roma.interfaces.StatusActionListener;
import tech.bigfig.roma.util.ViewDataUtils;
import tech.bigfig.roma.viewdata.StatusViewData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_ACCOUNT = 0;
    private static final int VIEW_TYPE_STATUS = 1;
    private static final int VIEW_TYPE_HASHTAG = 2;

    private List<Account> accountList;
    private List<Status> statusList;
    private List<StatusViewData.Concrete> concreteStatusList;
    private List<String> hashtagList;

    private boolean mediaPreviewsEnabled;
    private boolean alwaysShowSensitiveMedia;
    private boolean useAbsoluteTime;

    private LinkListener linkListener;
    private StatusActionListener statusListener;

    public SearchResultsAdapter(boolean mediaPreviewsEnabled,
                                boolean alwaysShowSensitiveMedia,
                                LinkListener linkListener,
                                StatusActionListener statusListener,
                                boolean useAbsoluteTime) {

        this.accountList = Collections.emptyList();
        this.statusList = Collections.emptyList();
        this.concreteStatusList = new ArrayList<>();
        this.hashtagList = Collections.emptyList();

        this.mediaPreviewsEnabled = mediaPreviewsEnabled;
        this.alwaysShowSensitiveMedia = alwaysShowSensitiveMedia;
        this.useAbsoluteTime = useAbsoluteTime;

        this.linkListener = linkListener;
        this.statusListener = statusListener;

    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
            case VIEW_TYPE_STATUS: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_status, parent, false);
                return new StatusViewHolder(view, useAbsoluteTime);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
            if (position >= accountList.size()) {
                if(position >= accountList.size() + concreteStatusList.size()) {
                    HashtagViewHolder holder = (HashtagViewHolder) viewHolder;
                    int index = position - accountList.size() - statusList.size();
                    holder.setup(hashtagList.get(index), linkListener);
                } else {
                    StatusViewHolder holder = (StatusViewHolder) viewHolder;
                    int index = position - accountList.size();
                    holder.setupWithStatus(concreteStatusList.get(index), statusListener, mediaPreviewsEnabled);
                }
            } else {
                AccountViewHolder holder = (AccountViewHolder) viewHolder;
                holder.setupWithAccount(accountList.get(position));
                holder.setupLinkListener(linkListener);
            }
        }

    @Override
    public int getItemCount() {
        return accountList.size() + hashtagList.size() + concreteStatusList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= accountList.size()) {
            if(position >= accountList.size() + concreteStatusList.size()) {
                return VIEW_TYPE_HASHTAG;
            } else {
                return VIEW_TYPE_STATUS;
            }
        } else {
            return VIEW_TYPE_ACCOUNT;
        }
    }

    public @Nullable Status getStatusAtPosition(int position) {
        return statusList.get(position - accountList.size());
    }

    public @Nullable StatusViewData.Concrete getConcreteStatusAtPosition(int position) {
        return concreteStatusList.get(position - accountList.size());
    }

    public void updateStatusAtPosition(StatusViewData.Concrete status, int position) {
        concreteStatusList.set(position - accountList.size(), status);
        notifyItemChanged(position);
    }

    public void removeStatusAtPosition(int position) {
        concreteStatusList.remove(position - accountList.size());
        notifyItemRemoved(position);
    }

    public void updateSearchResults(SearchResults results) {
        if (results != null) {
            accountList = results.getAccounts();
            statusList = results.getStatuses();
            for(Status status: results.getStatuses()) {
                concreteStatusList.add(ViewDataUtils.statusToViewData(
                        status,
                        alwaysShowSensitiveMedia
                ));
            }
            hashtagList = results.getHashtags();

        } else {
            accountList = Collections.emptyList();
            statusList = Collections.emptyList();
            concreteStatusList.clear();
            hashtagList = Collections.emptyList();

        }
        notifyDataSetChanged();
    }

    private static class HashtagViewHolder extends RecyclerView.ViewHolder {
        private TextView hashtag;

        HashtagViewHolder(View itemView) {
            super(itemView);
            hashtag = itemView.findViewById(R.id.hashtag);
        }

        void setup(final String tag, final LinkListener listener) {
            hashtag.setText(String.format("#%s", tag));
            hashtag.setOnClickListener(v -> listener.onViewTag(tag));
        }
    }
}
