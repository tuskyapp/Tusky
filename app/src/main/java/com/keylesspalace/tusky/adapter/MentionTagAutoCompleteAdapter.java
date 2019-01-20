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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by charlag on 12/11/17.
 */

public class MentionTagAutoCompleteAdapter extends BaseAdapter
        implements Filterable {
    private static final int ACCOUNT_VIEW_TYPE = 0;
    private static final int HASHTAG_VIEW_TYPE = 1;

    private final ArrayList<AutocompleteResult> resultList;
    private final AutocomletionProvider autocomletionProvider;
    private final Context context;

    public MentionTagAutoCompleteAdapter(Context context,
                                         AutocomletionProvider autocomletionProvider) {
        super();
        this.context = context;
        resultList = new ArrayList<>();
        this.autocomletionProvider = autocomletionProvider;
    }

    @Override
    public int getCount() {
        return resultList.size();
    }

    @Override
    public AutocompleteResult getItem(int index) {
        return resultList.get(index);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    @NonNull
    public Filter getFilter() {
        return new Filter() {
            @Override
            public CharSequence convertResultToString(Object resultValue) {
                if (resultValue instanceof AccountResult) {
                    return ((AccountResult) resultValue).account.getUsername();
                } else {
                    return formatHashtag((HashtagResult) resultValue);
                }
            }

            // This method is invoked in a worker thread.
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null) {
                    List<AutocompleteResult> results =
                            autocomletionProvider.search(constraint.toString());
                    filterResults.values = results;
                    filterResults.count = results.size();
                }
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    resultList.clear();
                    resultList.addAll((List<AutocompleteResult>) results.values);
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
    }

    @Override
    @NonNull
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;

        switch (getItemViewType(position)) {
            case ACCOUNT_VIEW_TYPE:
                AccountViewHolder holder;
                if (convertView == null) {
                    //noinspection ConstantConditions
                    view = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                            .inflate(R.layout.item_autocomplete_account, parent, false);
                }
                if (view.getTag() == null) {
                    view.setTag(new AccountViewHolder(view));
                }
                holder = (AccountViewHolder) view.getTag();

                AccountResult accountResult = ((AccountResult) getItem(position));
                if (accountResult != null) {
                    Account account = accountResult.account;
                    String format = context.getString(R.string.status_username_format);
                    String formattedUsername = String.format(format, account.getUsername());
                    holder.username.setText(formattedUsername);
                    CharSequence emojifiedName = CustomEmojiHelper.emojifyString(account.getName(),
                            account.getEmojis(), holder.displayName);
                    holder.displayName.setText(emojifiedName);
                    if (!account.getAvatar().isEmpty()) {
                        Picasso.with(context)
                                .load(account.getAvatar())
                                .placeholder(R.drawable.avatar_default)
                                .into(holder.avatar);
                    }
                }
                break;

            case HASHTAG_VIEW_TYPE:
                if (convertView == null) {
                    view = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                            .inflate(R.layout.item_hashtag, parent, false);
                }

                HashtagResult result = (HashtagResult) getItem(position);
                if (result != null) {
                    ((TextView) view).setText(formatHashtag(result));
                }
                break;
            default:
                throw new AssertionError("unknown view type");
        }

        return view;
    }

    private String formatHashtag(HashtagResult result) {
        return String.format("#%s", result.hashtag);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position) instanceof AccountResult) {
            return ACCOUNT_VIEW_TYPE;
        } else {
            return HASHTAG_VIEW_TYPE;
        }
    }

    public abstract static class AutocompleteResult {
        AutocompleteResult() {
        }
    }

    public final static class AccountResult extends AutocompleteResult {
        private final Account account;

        public AccountResult(Account account) {
            this.account = account;
        }
    }

    public final static class HashtagResult extends AutocompleteResult {
        private final String hashtag;

        public HashtagResult(String hashtag) {
            this.hashtag = hashtag;
        }
    }

    public interface AutocomletionProvider {
        List<AutocompleteResult> search(String mention);
    }

    private class AccountViewHolder {
        final TextView username;
        final TextView displayName;
        final ImageView avatar;

        private AccountViewHolder(View view) {
            username = view.findViewById(R.id.username);
            displayName = view.findViewById(R.id.display_name);
            avatar = view.findViewById(R.id.avatar);
        }
    }
}
