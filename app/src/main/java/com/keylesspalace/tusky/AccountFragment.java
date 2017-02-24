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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountFragment extends Fragment implements AccountActionListener,
        FooterActionListener {
    private static final String TAG = "Account"; // logging tag and Volley request tag

    public enum Type {
        FOLLOWS,
        FOLLOWERS,
        BLOCKS,
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

    public static AccountFragment newInstance(Type type) {
        Bundle arguments = new Bundle();
        AccountFragment fragment = new AccountFragment();
        arguments.putString("type", type.name());
        fragment.setArguments(arguments);
        return fragment;
    }

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

    @Override
    public void onDestroy() {
        VolleySingleton.getInstance(getContext()).cancelAll(TAG);
        super.onDestroy();
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
        Drawable drawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
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
        if (type == Type.BLOCKS) {
            adapter = new BlocksAdapter(this, this);
        } else {
            adapter = new FollowAdapter(this, this);
        }
        recyclerView.setAdapter(adapter);

        if (jumpToTopAllowed()) {
            TabLayout layout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
            onTabSelectedListener = new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                    jumpToTop();
                }
            };
            layout.addOnTabSelectedListener(onTabSelectedListener);
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        if (jumpToTopAllowed()) {
            TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
            tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        }
        super.onDestroyView();
    }

    private void fetchAccounts(final String fromId) {
        String endpoint;
        switch (type) {
            default:
            case FOLLOWS: {
                endpoint = String.format(getString(R.string.endpoint_following), accountId);
                break;
            }
            case FOLLOWERS: {
                endpoint = String.format(getString(R.string.endpoint_followers), accountId);
                break;
            }
            case BLOCKS: {
                endpoint = getString(R.string.endpoint_blocks);
                break;
            }
        }
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
                        onFetchAccountsSuccess(accounts, fromId);
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
        request.setTag(TAG);
        VolleySingleton.getInstance(getContext()).addToRequestQueue(request);
    }

    private void fetchAccounts() {
        fetchAccounts(null);
    }

    private static boolean findAccount(List<Account> accounts, String id) {
        for (Account account : accounts) {
            if (account.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void onFetchAccountsSuccess(List<Account> accounts, String fromId) {
        if (fromId != null) {
            if (accounts.size() > 0 && !findAccount(accounts, fromId)) {
                setFetchTimelineState(FooterViewHolder.State.LOADING);
                adapter.addItems(accounts);
            } else {
                setFetchTimelineState(FooterViewHolder.State.END_OF_TIMELINE);
            }
        } else {
            adapter.update(accounts);
        }
    }

    private void onFetchAccountsFailure(Exception exception) {
        setFetchTimelineState(FooterViewHolder.State.RETRY);
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
    }

    private void setFetchTimelineState(FooterViewHolder.State state) {
        // Set the adapter to set its state when it's bound, if the current Footer is offscreen.
        adapter.setFooterState(state);
        // Check if it's onscreen, and update it directly if it is.
        RecyclerView.ViewHolder viewHolder =
                recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
        if (viewHolder != null) {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(state);
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

    public void onBlock(final boolean block, final String id, final int position) {
        String endpoint;
        if (!block) {
            endpoint = String.format(getString(R.string.endpoint_unblock), id);
        } else {
            endpoint = String.format(getString(R.string.endpoint_block), id);
        }
        String url = "https://" + domain + endpoint;
        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        onBlockSuccess(block, position);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onBlockFailure(block, id);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        request.setTag(TAG);
        VolleySingleton.getInstance(getContext()).addToRequestQueue(request);
    }

    private void onBlockSuccess(boolean blocked, int position) {
        BlocksAdapter blocksAdapter = (BlocksAdapter) adapter;
        blocksAdapter.setBlocked(blocked, position);
    }

    private void onBlockFailure(boolean block, String id) {
        String verb;
        if (block) {
            verb = "block";
        } else {
            verb = "unblock";
        }
        Log.e(TAG, String.format("Failed to %s account id %s", verb, id));
    }

    private boolean jumpToTopAllowed() {
        return type != Type.BLOCKS;
    }

    private void jumpToTop() {
        layoutManager.scrollToPositionWithOffset(0, 0);
        scrollListener.reset();
    }
}
