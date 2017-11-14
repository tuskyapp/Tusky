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
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by charlag on 12/11/17.
 */

public class MentionAutoCompleteAdapter extends ArrayAdapter<Account>
        implements Filterable {
    private ArrayList<Account> resultList;
    @LayoutRes
    private int layoutId;
    private final AccountSearchProvider accountSearchProvider;

    public MentionAutoCompleteAdapter(Context context, @LayoutRes int resource,
                               AccountSearchProvider accountSearchProvider) {
        super(context, resource);
        layoutId = resource;
        resultList = new ArrayList<>();
        this.accountSearchProvider = accountSearchProvider;
    }

    @Override
    public int getCount() {
        return resultList.size();
    }

    @Override
    public Account getItem(int index) {
        return resultList.get(index);
    }

    @Override
    @NonNull
    public Filter getFilter() {
        return new Filter() {
            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return ((Account) resultValue).username;
            }

            // This method is invoked in a worker thread.
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null) {
                    List<Account> accounts =
                            accountSearchProvider.searchAccounts(constraint.toString());
                    filterResults.values = accounts;
                    filterResults.count = accounts.size();
                }
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    resultList.clear();
                    ArrayList<Account> newResults = (ArrayList<Account>) results.values;
                    resultList.addAll(newResults);
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

        Context context = getContext();

        if (convertView == null) {
            LayoutInflater layoutInflater =
                    (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //noinspection ConstantConditions
            view = layoutInflater.inflate(layoutId, parent, false);
        }

        Account account = getItem(position);
        if (account != null) {
            TextView username = view.findViewById(R.id.username);
            TextView displayName = view.findViewById(R.id.display_name);
            ImageView avatar = view.findViewById(R.id.avatar);
            String format = getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.username);
            username.setText(formattedUsername);
            displayName.setText(account.getDisplayName());
            if (!account.avatar.isEmpty()) {
                Picasso.with(context)
                        .load(account.avatar)
                        .placeholder(R.drawable.avatar_default)
                        .transform(new RoundedTransformation(7, 0))
                        .into(avatar);
            }
        }

        return view;
    }

    public interface AccountSearchProvider {
        List<Account> searchAccounts(String mention);
    }
}
