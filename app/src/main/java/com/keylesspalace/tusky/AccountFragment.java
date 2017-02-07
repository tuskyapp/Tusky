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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountFragment extends Fragment implements AccountActionListener,
        FooterActionListener {
    private static final String TAG = "Account";

    public enum Type {
        FOLLOWS,
        FOLLOWERS,
    }

    private Type type;
    private String accountId;
    private String domain;
    private String accessToken;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private AccountAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;

    public static AccountFragment newInstance(Type type, String accountId) {
        Bundle arguments = new Bundle();
        AccountFragment fragment = new AccountFragment();
        arguments.putString("type", type.name());
        arguments.putString("accountId", accountId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        type = Type.valueOf(arguments.getString("type"));
        accountId = arguments.getString("accountId");

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_account, container, false);

        Context context = getContext();
        recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.status_divider);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                AccountAdapter adapter = (AccountAdapter) view.getAdapter();
                Account account = adapter.getItem(adapter.getItemCount() - 2);
                if (account != null) {
                    fetchAccounts(account.id);
                } else {
                    fetchAccounts();
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
        adapter = new AccountAdapter(this, this);
        recyclerView.setAdapter(adapter);

        TabLayout layout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
        onTabSelectedListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {}

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                jumpToTop();
            }
        };
        layout.addOnTabSelectedListener(onTabSelectedListener);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
        tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        super.onDestroyView();
    }

    private void fetchAccounts(final String fromId) {
        int endpointId;
        switch (type) {
            default:
            case FOLLOWS: {
                endpointId = R.string.endpoint_following;
                break;
            }
            case FOLLOWERS: {
                endpointId = R.string.endpoint_followers;
                break;
            }
        }
        String endpoint = String.format(getString(endpointId), accountId);
        String url = "https://" + domain + endpoint;
        if (fromId != null) {
            url += "?max_id=" + fromId;
        }
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        List<Account> accounts;
                        try {
                            accounts = Account.parse(response);
                        } catch (JSONException e) {
                            onFetchAccountsFailure(e);
                            return;
                        }
                        onFetchAccountsSuccess(accounts, fromId != null);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onFetchAccountsFailure(error);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(getContext()).addToRequestQueue(request);
    }

    private void fetchAccounts() {
        fetchAccounts(null);
    }

    private void onFetchAccountsSuccess(List<Account> accounts, boolean added) {
        if (added) {
            adapter.addItems(accounts);
        } else {
            adapter.update(accounts);
        }
        showFetchAccountsRetry(false);
    }

    private void onFetchAccountsFailure(Exception exception) {
        showFetchAccountsRetry(true);
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
    }

    private void showFetchAccountsRetry(boolean show) {
        RecyclerView.ViewHolder viewHolder =
                recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
        if (viewHolder != null) {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.showRetry(show);
        }
    }

    public void onLoadMore() {
        Account account = adapter.getItem(adapter.getItemCount() - 2);
        if (account != null) {
            fetchAccounts(account.id);
        } else {
            fetchAccounts();
        }
    }

    public void onViewAccount(String id) {
        Intent intent = new Intent(getContext(), AccountActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    private void jumpToTop() {
        layoutManager.scrollToPositionWithOffset(0, 0);
        scrollListener.reset();
    }
}
