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

package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.entity.Status;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;

public class TimelineFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener {
    private static final String TAG = "Timeline"; // logging tag

    private Call<List<Status>> listCall;

    enum Kind {
        HOME,
        PUBLIC_LOCAL,
        PUBLIC_FEDERATED,
        TAG,
        USER,
        FAVOURITES
    }

    private SwipeRefreshLayout swipeRefreshLayout;
    private TimelineAdapter adapter;
    private Kind kind;
    private String hashtagOrId;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;

    public static TimelineFragment newInstance(Kind kind) {
        TimelineFragment fragment = new TimelineFragment();
        Bundle arguments = new Bundle();
        arguments.putString("kind", kind.name());
        fragment.setArguments(arguments);
        return fragment;
    }

    public static TimelineFragment newInstance(Kind kind, String hashtagOrId) {
        TimelineFragment fragment = new TimelineFragment();
        Bundle arguments = new Bundle();
        arguments.putString("kind", kind.name());
        arguments.putString("hashtag_or_id", hashtagOrId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
             Bundle savedInstanceState) {

        Bundle arguments = getArguments();
        kind = Kind.valueOf(arguments.getString("kind"));
        if (kind == Kind.TAG || kind == Kind.USER) {
            hashtagOrId = arguments.getString("hashtag_or_id");
        }

        final View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        // Setup the SwipeRefreshLayout.
        Context context = getContext();
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Setup the RecyclerView.
        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
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
            public void onScrolled(RecyclerView view, int dx, int dy) {
                super.onScrolled(view, dx, dy);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

                if (dy > 0 && prefs.getBoolean("fabHide", false) && MainActivity.composeBtn.isShown()) {
                    MainActivity.composeBtn.hide(); // hides the button if we're scrolling down
                } else if (dy < 0 && prefs.getBoolean("fabHide", false) && !MainActivity.composeBtn.isShown()) {
                    MainActivity.composeBtn.show(); // shows it if we are scrolling up
                }

            }

            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                TimelineAdapter adapter = (TimelineAdapter) view.getAdapter();
                Status status = adapter.getItem(adapter.getItemCount() - 2);
                if (status != null) {
                    sendFetchTimelineRequest(status.id, null);
                } else {
                    sendFetchTimelineRequest();
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
        adapter = new TimelineAdapter(this);
        recyclerView.setAdapter(adapter);

        if (jumpToTopAllowed()) {
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
        }

        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listCall != null) listCall.cancel();
    }

    @Override
    public void onDestroyView() {
        if (jumpToTopAllowed()) {
            TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
            tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        }
        super.onDestroyView();
    }

    private boolean jumpToTopAllowed() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES;
    }

    private void jumpToTop() {
        layoutManager.scrollToPosition(0);
        scrollListener.reset();
    }

    private void sendFetchTimelineRequest(@Nullable final String fromId, @Nullable String uptoId) {
        MastodonAPI api = ((BaseActivity) getActivity()).mastodonAPI;

        Callback<List<Status>> cb = new Callback<List<Status>>() {
            @Override
            public void onResponse(Call<List<Status>> call, retrofit2.Response<List<Status>> response) {
                if (response.isSuccessful()) {
                    onFetchTimelineSuccess(response.body(), fromId);
                } else {
                    onFetchTimelineFailure(new Exception(response.message()));
                }
            }

            @Override
            public void onFailure(Call<List<Status>> call, Throwable t) {
                onFetchTimelineFailure((Exception) t);
            }
        };

        switch (kind) {
            default:
            case HOME: {
                listCall = api.homeTimeline(fromId, uptoId, null);
                break;
            }
            case PUBLIC_FEDERATED: {
                listCall = api.publicTimeline(null, fromId, uptoId, null);
                break;
            }
            case PUBLIC_LOCAL: {
                listCall = api.publicTimeline(true, fromId, uptoId, null);
                break;
            }
            case TAG: {
                listCall = api.hashtagTimeline(hashtagOrId, null, fromId, uptoId, null);
                break;
            }
            case USER: {
                listCall = api.accountStatuses(hashtagOrId, fromId, uptoId, null);
                break;
            }
            case FAVOURITES: {
                listCall = api.favourites(fromId, uptoId, null);
                break;
            }
        }
        callList.add(listCall);
        listCall.enqueue(cb);
    }

    private void sendFetchTimelineRequest() {
        sendFetchTimelineRequest(null, null);
    }

    private static boolean findStatus(List<Status> statuses, String id) {
        for (Status status : statuses) {
            if (status.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void onFetchTimelineSuccess(List<Status> statuses, String fromId) {
        if (fromId != null) {
            if (statuses.size() > 0 && !findStatus(statuses, fromId)) {
                adapter.addItems(statuses);
            }
        } else {
            adapter.update(statuses);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    public void onFetchTimelineFailure(Exception exception) {
        swipeRefreshLayout.setRefreshing(false);
        Log.e(TAG, "Fetch Failure: " + exception.getMessage());
    }

    public void onRefresh() {
        Status status = adapter.getItem(0);
        if (status != null) {
            sendFetchTimelineRequest(null, status.id);
        } else {
            sendFetchTimelineRequest();
        }
    }

    public void onReply(int position) {
        super.reply(adapter.getItem(position));
    }

    public void onReblog(final boolean reblog, final int position) {
        super.reblog(adapter.getItem(position), reblog, adapter, position);
    }

    public void onFavourite(final boolean favourite, final int position) {
        super.favourite(adapter.getItem(position), favourite, adapter, position);
    }

    public void onMore(View view, final int position) {
        super.more(adapter.getItem(position), view, adapter, position);
    }

    public void onViewMedia(String url, Status.MediaAttachment.Type type) {
        super.viewMedia(url, type);
    }

    public void onViewThread(int position) {
        super.viewThread(adapter.getItem(position));
    }

    public void onViewTag(String tag) {
        if (kind == Kind.TAG && hashtagOrId.equals(tag)) {
            // If already viewing a tag page, then ignore any request to view that tag again.
            return;
        }
        super.viewTag(tag);
    }

    public void onViewAccount(String id) {
        if (kind == Kind.USER && hashtagOrId.equals(id)) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return;
        }
        super.viewAccount(id);
    }
}
