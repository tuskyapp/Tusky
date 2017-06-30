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

package com.keylesspalace.tusky.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import com.keylesspalace.tusky.AccountActivity;
import com.keylesspalace.tusky.adapter.AccountAdapter;
import com.keylesspalace.tusky.adapter.BlocksAdapter;
import com.keylesspalace.tusky.adapter.FollowAdapter;
import com.keylesspalace.tusky.adapter.FollowRequestsAdapter;
import com.keylesspalace.tusky.adapter.FooterViewHolder;
import com.keylesspalace.tusky.adapter.MutesAdapter;
import com.keylesspalace.tusky.BaseActivity;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Relationship;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AccountListFragment extends BaseFragment implements AccountActionListener {
    private static final String TAG = "AccountList"; // logging tag

    public enum Type {
        FOLLOWS,
        FOLLOWERS,
        BLOCKS,
        MUTES,
        FOLLOW_REQUESTS,
    }

    private Type type;
    private String accountId;
    private LinearLayoutManager layoutManager;
    private RecyclerView recyclerView;
    private EndlessOnScrollListener scrollListener;
    private AccountAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private MastodonApi api;
    private boolean bottomLoading;
    private int bottomFetches;
    private boolean topLoading;
    private int topFetches;

    public static AccountListFragment newInstance(Type type) {
        Bundle arguments = new Bundle();
        AccountListFragment fragment = new AccountListFragment();
        arguments.putSerializable("type", type);
        fragment.setArguments(arguments);
        return fragment;
    }

    public static AccountListFragment newInstance(Type type, String accountId) {
        Bundle arguments = new Bundle();
        AccountListFragment fragment = new AccountListFragment();
        arguments.putSerializable("type", type);
        arguments.putString("accountId", accountId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        type = (Type) arguments.getSerializable("type");
        accountId = arguments.getString("accountId");
        api = null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_account_list, container, false);

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
        scrollListener = null;
        if (type == Type.BLOCKS) {
            adapter = new BlocksAdapter(this);
        } else if (type == Type.MUTES) {
            adapter = new MutesAdapter(this);
        } else if (type == Type.FOLLOW_REQUESTS) {
            adapter = new FollowRequestsAdapter(this);
        } else {
            adapter = new FollowAdapter(this);
        }
        recyclerView.setAdapter(adapter);

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        BaseActivity activity = (BaseActivity) getActivity();

        if (jumpToTopAllowed()) {
            TabLayout layout = (TabLayout) activity.findViewById(R.id.tab_layout);
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
        }

        /* MastodonApi on the base activity is only guaranteed to be initialised after the parent
         * activity is created, so everything needing to access the api object has to be delayed
         * until here. */
        api = activity.mastodonApi;
        scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                AccountListFragment.this.onLoadMore(view);
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
    }

    @Override
    public void onDestroyView() {
        if (jumpToTopAllowed()) {
            TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
            tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        }
        super.onDestroyView();
    }

    @Override
    public void onViewAccount(String id) {
        Intent intent = new Intent(getContext(), AccountActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    @Override
    public void onMute(final boolean mute, final String id, final int position) {
        if (api == null) {
            /* If somehow an unmute button is clicked after onCreateView but before
             * onActivityCreated, then this would get called with a null api object, so this eats
             * that input. */
            Log.d(TAG, "MastodonApi isn't initialised so this mute can't occur.");
            return;
        }

        Callback<Relationship> callback = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    onMuteSuccess(mute, id, position);
                } else {
                    onMuteFailure(mute, id);
                }
            }

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {
                onMuteFailure(mute, id);
            }
        };

        Call<Relationship> call;
        if (!mute) {
            call = api.unmuteAccount(id);
        } else {
            call = api.muteAccount(id);
        }
        callList.add(call);
        call.enqueue(callback);
    }

    private void onMuteSuccess(boolean muted, final String id, final int position) {
        if (muted) {
            return;
        }
        final MutesAdapter mutesAdapter = (MutesAdapter) adapter;
        final Account unmutedUser = mutesAdapter.removeItem(position);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mutesAdapter.addItem(unmutedUser, position);
                onMute(true, id, position);
            }
        };
        Snackbar.make(recyclerView, R.string.confirmation_unmuted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, listener)
                .show();
    }

    private void onMuteFailure(boolean mute, String id) {
        String verb;
        if (mute) {
            verb = "mute";
        } else {
            verb = "unmute";
        }
        Log.e(TAG, String.format("Failed to %s account id %s", verb, id));
    }

    @Override
    public void onBlock(final boolean block, final String id, final int position) {
        if (api == null) {
            /* If somehow an unblock button is clicked after onCreateView but before
             * onActivityCreated, then this would get called with a null api object, so this eats
             * that input. */
            Log.d(TAG, "MastodonApi isn't initialised so this block can't occur.");
            return;
        }

        Callback<Relationship> cb = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    onBlockSuccess(block, id, position);
                } else {
                    onBlockFailure(block, id);
                }
            }

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {
                onBlockFailure(block, id);
            }
        };

        Call<Relationship> call;
        if (!block) {
            call = api.unblockAccount(id);
        } else {
            call = api.blockAccount(id);
        }
        callList.add(call);
        call.enqueue(cb);
    }

    private void onBlockSuccess(boolean blocked, final String id, final int position) {
        if (blocked) {
            return;
        }
        final BlocksAdapter blocksAdapter = (BlocksAdapter) adapter;
        final Account unblockedUser = blocksAdapter.removeItem(position);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blocksAdapter.addItem(unblockedUser, position);
                onBlock(true, id, position);
            }
        };
        Snackbar.make(recyclerView, R.string.confirmation_unblocked, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, listener)
                .show();
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

    @Override
    public void onRespondToFollowRequest(final boolean accept, final String accountId,
                                         final int position) {
        if (api == null) {
            /* If somehow an response button is clicked after onCreateView but before
             * onActivityCreated, then this would get called with a null api object, so this eats
             * that input. */
            Log.d(TAG, "MastodonApi isn't initialised, so follow requests can't be responded to.");
            return;
        }

        Callback<Relationship> callback = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    onRespondToFollowRequestSuccess(position);
                } else {
                    onRespondToFollowRequestFailure(accept, accountId);
                }
            }

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {
                onRespondToFollowRequestFailure(accept, accountId);
            }
        };

        Call<Relationship> call;
        if (accept) {
            call = api.authorizeFollowRequest(accountId);
        } else {
            call = api.rejectFollowRequest(accountId);
        }
        callList.add(call);
        call.enqueue(callback);
    }

    private void onRespondToFollowRequestSuccess(int position) {
        FollowRequestsAdapter followRequestsAdapter = (FollowRequestsAdapter) adapter;
        followRequestsAdapter.removeItem(position);
    }

    private void onRespondToFollowRequestFailure(boolean accept, String accountId) {
        String verb;
        if (accept) {
            verb = "accept";
        } else {
            verb = "reject";
        }
        String message = String.format("Failed to %s account id %s.", verb, accountId);
        Log.e(TAG, message);
    }

    private boolean jumpToTopAllowed() {
        return type == Type.FOLLOWS || type == Type.FOLLOWERS;
    }

    private void jumpToTop() {
        layoutManager.scrollToPositionWithOffset(0, 0);
        scrollListener.reset();
    }

    private enum FetchEnd {
        TOP,
        BOTTOM
    }

    private Call<List<Account>> getFetchCallByListType(Type type, String fromId, String uptoId) {
        switch (type) {
            default:
            case FOLLOWS:         return api.accountFollowing(accountId, fromId, uptoId, null);
            case FOLLOWERS:       return api.accountFollowers(accountId, fromId, uptoId, null);
            case BLOCKS:          return api.blocks(fromId, uptoId, null);
            case MUTES:           return api.mutes(fromId, uptoId, null);
            case FOLLOW_REQUESTS: return api.followRequests(fromId, uptoId, null);
        }
    }

    private void fetchAccounts(String fromId, String uptoId, final FetchEnd fetchEnd) {
        /* If there is a fetch already ongoing, record however many fetches are requested and
         * fulfill them after it's complete. */
        if (fetchEnd == FetchEnd.TOP && topLoading) {
            topFetches++;
            return;
        }
        if (fetchEnd == FetchEnd.BOTTOM && bottomLoading) {
            bottomFetches++;
            return;
        }

        if (fromId != null || adapter.getItemCount() <= 1) {
            setFooterState(FooterViewHolder.State.LOADING);
        }

        Callback<List<Account>> cb = new Callback<List<Account>>() {
            @Override
            public void onResponse(Call<List<Account>> call, Response<List<Account>> response) {
                if (response.isSuccessful()) {
                    String linkHeader = response.headers().get("Link");
                    onFetchAccountsSuccess(response.body(), linkHeader, fetchEnd);
                } else {
                    onFetchAccountsFailure(new Exception(response.message()), fetchEnd);
                }
            }

            @Override
            public void onFailure(Call<List<Account>> call, Throwable t) {
                onFetchAccountsFailure((Exception) t, fetchEnd);
            }
        };
        Call<List<Account>> listCall = getFetchCallByListType(type, fromId, uptoId);
        callList.add(listCall);
        listCall.enqueue(cb);
    }

    private void onFetchAccountsSuccess(List<Account> accounts, String linkHeader,
            FetchEnd fetchEnd) {
        List<HttpHeaderLink> links = HttpHeaderLink.parse(linkHeader);
        switch (fetchEnd) {
            case TOP: {
                HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                String uptoId = null;
                if (previous != null) {
                    uptoId = previous.uri.getQueryParameter("since_id");
                }
                adapter.update(accounts, null, uptoId);
                break;
            }
            case BOTTOM: {
                HttpHeaderLink next = HttpHeaderLink.findByRelationType(links, "next");
                String fromId = null;
                if (next != null) {
                    fromId = next.uri.getQueryParameter("max_id");
                }
                if (adapter.getItemCount() > 1) {
                    adapter.addItems(accounts, fromId);
                } else {
                    /* If this is the first fetch, also save the id from the "previous" link and
                     * treat this operation as a refresh so the scroll position doesn't get pushed
                     * down to the end. */
                    HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                    String uptoId = null;
                    if (previous != null) {
                        uptoId = previous.uri.getQueryParameter("since_id");
                    }
                    adapter.update(accounts, fromId, uptoId);
                }
                break;
            }
        }
        fulfillAnyQueuedFetches(fetchEnd);
        if (accounts.size() == 0 && adapter.getItemCount() == 1) {
            setFooterState(FooterViewHolder.State.EMPTY);
        } else {
            setFooterState(FooterViewHolder.State.END);
        }
    }

    private void onFetchAccountsFailure(Exception exception, FetchEnd fetchEnd) {
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
        fulfillAnyQueuedFetches(fetchEnd);
    }

    /* This needs to be called from the endless scroll listener, which does not allow notifying the
     * adapter during the callback. So, this is the workaround. */
    private void setFooterState(FooterViewHolder.State state) {
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

    private void onRefresh() {
        fetchAccounts(null, adapter.getTopId(), FetchEnd.TOP);
    }

    private void onLoadMore(RecyclerView recyclerView) {
        AccountAdapter adapter = (AccountAdapter) recyclerView.getAdapter();
        fetchAccounts(adapter.getBottomId(), null, FetchEnd.BOTTOM);
    }

    private void fulfillAnyQueuedFetches(FetchEnd fetchEnd) {
        switch (fetchEnd) {
            case BOTTOM: {
                bottomLoading = false;
                if (bottomFetches > 0) {
                    bottomFetches--;
                    onLoadMore(recyclerView);
                }
                break;
            }
            case TOP: {
                topLoading = false;
                if (topFetches > 0) {
                    topFetches--;
                    onRefresh();
                }
                break;
            }
        }
    }
}
