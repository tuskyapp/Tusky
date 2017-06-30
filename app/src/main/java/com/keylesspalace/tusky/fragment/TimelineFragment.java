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
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.MainActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.TimelineAdapter;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;

import java.util.Iterator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TimelineFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener,
        StatusActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "Timeline"; // logging tag

    public enum Kind {
        HOME,
        PUBLIC_LOCAL,
        PUBLIC_FEDERATED,
        TAG,
        USER,
        FAVOURITES
    }

    private enum FetchEnd {
        TOP,
        BOTTOM,
    }

    private SwipeRefreshLayout swipeRefreshLayout;
    private TimelineAdapter adapter;
    private Kind kind;
    private String hashtagOrId;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private boolean filterRemoveReplies;
    private boolean filterRemoveReblogs;
    private boolean hideFab;
    private TimelineReceiver timelineReceiver;
    private boolean topLoading;
    private int topFetches;
    private boolean bottomLoading;
    private int bottomFetches;

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
        adapter = new TimelineAdapter(this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);
        boolean mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true);
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        recyclerView.setAdapter(adapter);

        timelineReceiver = new TimelineReceiver(adapter, this);
        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .registerReceiver(timelineReceiver, TimelineReceiver.getFilter(kind));

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

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

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then. */
        if (composeButtonPresent()) {
            /* Use a modified scroll listener that both loads more statuses as it goes, and hides
             * the follow button on down-scroll. */
            MainActivity activity = (MainActivity) getActivity();
            final FloatingActionButton composeButton = activity.composeButton;
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                    activity);
            hideFab = preferences.getBoolean("fabHide", false);
            scrollListener = new EndlessOnScrollListener(layoutManager) {
                @Override
                public void onScrolled(RecyclerView view, int dx, int dy) {
                    super.onScrolled(view, dx, dy);

                    if (hideFab) {
                        if (dy > 0 && composeButton.isShown()) {
                            composeButton.hide(); // hides the button if we're scrolling down
                        } else if (dy < 0 && !composeButton.isShown()) {
                            composeButton.show(); // shows it if we are scrolling up
                        }
                    } else if (!composeButton.isShown()) {
                        composeButton.show();
                    }
                }

                @Override
                public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                    TimelineFragment.this.onLoadMore(view);
                }
            };
        } else {
            // Just use the basic scroll listener to load more statuses.
            scrollListener = new EndlessOnScrollListener(layoutManager) {
                @Override
                public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                    TimelineFragment.this.onLoadMore(view);
                }
            };
        }
        recyclerView.addOnScrollListener(scrollListener);
    }

    @Override
    public void onDestroyView() {
        if (jumpToTopAllowed()) {
            TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
            tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        }
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(timelineReceiver);
        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        sendFetchTimelineRequest(null, adapter.getTopId(), FetchEnd.TOP);
    }

    @Override
    public void onReply(int position) {
        super.reply(adapter.getItem(position));
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        super.reblog(adapter.getItem(position), reblog, adapter, position);
    }

    @Override
    public void onFavourite(final boolean favourite, final int position) {
        super.favourite(adapter.getItem(position), favourite, adapter, position);
    }

    @Override
    public void onMore(View view, final int position) {
        super.more(adapter.getItem(position), view, adapter, position);
    }

    @Override
    public void onViewMedia(String[] urls, int urlIndex, Status.MediaAttachment.Type type) {
        super.viewMedia(urls, urlIndex, type);
    }

    @Override
    public void onViewThread(int position) {
        super.viewThread(adapter.getItem(position));
    }

    @Override
    public void onViewTag(String tag) {
        if (kind == Kind.TAG && hashtagOrId.equals(tag)) {
            // If already viewing a tag page, then ignore any request to view that tag again.
            return;
        }
        super.viewTag(tag);
    }

    @Override
    public void onViewAccount(String id) {
        if (kind == Kind.USER && hashtagOrId.equals(id)) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return;
        }
        super.viewAccount(id);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "fabHide": {
                hideFab = sharedPreferences.getBoolean("fabHide", false);
                break;
            }
            case "mediaPreviewEnabled": {
                boolean enabled = sharedPreferences.getBoolean("mediaPreviewEnabled", true);
                adapter.setMediaPreviewEnabled(enabled);
                fullyRefresh();
                break;
            }
            case "tabFilterHomeReplies": {
                boolean filter = sharedPreferences.getBoolean("tabFilterHomeReplies", true);
                boolean oldRemoveReplies = filterRemoveReplies;
                filterRemoveReplies = kind == Kind.HOME && !filter;
                if (adapter.getItemCount() > 1 && oldRemoveReplies != filterRemoveReplies) {
                    fullyRefresh();
                }
                break;
            }
            case "tabFilterHomeBoosts": {
                boolean filter = sharedPreferences.getBoolean("tabFilterHomeBoosts", true);
                boolean oldRemoveReblogs = filterRemoveReblogs;
                filterRemoveReblogs = kind == Kind.HOME && !filter;
                if (adapter.getItemCount() > 1 && oldRemoveReblogs != filterRemoveReblogs) {
                    fullyRefresh();
                }
                break;
            }
        }
    }

    private void onLoadMore(RecyclerView view) {
        TimelineAdapter adapter = (TimelineAdapter) view.getAdapter();
        sendFetchTimelineRequest(adapter.getBottomId(), null, FetchEnd.BOTTOM);
    }

    private void fullyRefresh() {
        adapter.clear();
        sendFetchTimelineRequest(null, null, FetchEnd.TOP);
    }

    private boolean jumpToTopAllowed() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES;
    }

    private boolean composeButtonPresent() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES && kind != Kind.USER;
    }

    private void jumpToTop() {
        layoutManager.scrollToPosition(0);
        scrollListener.reset();
    }

    private Call<List<Status>> getFetchCallByTimelineType(Kind kind, String tagOrId, String fromId,
            String uptoId) {
        MastodonApi api = mastodonApi;
        switch (kind) {
            default:
            case HOME:             return api.homeTimeline(fromId, uptoId, null);
            case PUBLIC_FEDERATED: return api.publicTimeline(null, fromId, uptoId, null);
            case PUBLIC_LOCAL:     return api.publicTimeline(true, fromId, uptoId, null);
            case TAG:              return api.hashtagTimeline(tagOrId, null, fromId, uptoId, null);
            case USER:             return api.accountStatuses(tagOrId, fromId, uptoId, null);
            case FAVOURITES:       return api.favourites(fromId, uptoId, null);
        }
    }

    private void sendFetchTimelineRequest(@Nullable String fromId, @Nullable String uptoId,
            final FetchEnd fetchEnd) {
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
            adapter.setFooterState(TimelineAdapter.FooterState.LOADING);
        }

        Callback<List<Status>> callback = new Callback<List<Status>>() {
            @Override
            public void onResponse(Call<List<Status>> call, Response<List<Status>> response) {
                if (response.isSuccessful()) {
                    String linkHeader = response.headers().get("Link");
                    onFetchTimelineSuccess(response.body(), linkHeader, fetchEnd);
                } else {
                    onFetchTimelineFailure(new Exception(response.message()), fetchEnd);
                }
            }

            @Override
            public void onFailure(Call<List<Status>> call, Throwable t) {
                onFetchTimelineFailure((Exception) t, fetchEnd);
            }
        };

        Call<List<Status>> listCall = getFetchCallByTimelineType(kind, hashtagOrId, fromId, uptoId);
        callList.add(listCall);
        listCall.enqueue(callback);
    }

    public void onFetchTimelineSuccess(List<Status> statuses, String linkHeader,
            FetchEnd fetchEnd) {
        filterStatuses(statuses);
        List<HttpHeaderLink> links = HttpHeaderLink.parse(linkHeader);
        switch (fetchEnd) {
            case TOP: {
                HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                String uptoId = null;
                if (previous != null) {
                    uptoId = previous.uri.getQueryParameter("since_id");
                }
                adapter.update(statuses, null, uptoId);
                break;
            }
            case BOTTOM: {
                HttpHeaderLink next = HttpHeaderLink.findByRelationType(links, "next");
                String fromId = null;
                if (next != null) {
                    fromId = next.uri.getQueryParameter("max_id");
                }
                if (adapter.getItemCount() > 1) {
                    adapter.addItems(statuses, fromId);
                } else {
                    /* If this is the first fetch, also save the id from the "previous" link and
                     * treat this operation as a refresh so the scroll position doesn't get pushed
                     * down to the end. */
                    HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                    String uptoId = null;
                    if (previous != null) {
                        uptoId = previous.uri.getQueryParameter("since_id");
                    }
                    adapter.update(statuses, fromId, uptoId);
                }
                break;
            }
        }
        fulfillAnyQueuedFetches(fetchEnd);
        if (statuses.size() == 0 && adapter.getItemCount() == 1) {
            adapter.setFooterState(TimelineAdapter.FooterState.EMPTY);
        } else {
            adapter.setFooterState(TimelineAdapter.FooterState.END);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    public void onFetchTimelineFailure(Exception exception, FetchEnd fetchEnd) {
        swipeRefreshLayout.setRefreshing(false);
        Log.e(TAG, "Fetch Failure: " + exception.getMessage());
        fulfillAnyQueuedFetches(fetchEnd);
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

    protected void filterStatuses(List<Status> statuses) {
        Iterator<Status> it = statuses.iterator();
        while (it.hasNext()) {
            Status status = it.next();
            if ((status.inReplyToId != null && filterRemoveReplies)
                    || (status.reblog != null && filterRemoveReblogs)) {
                it.remove();
            }
        }
    }
}
