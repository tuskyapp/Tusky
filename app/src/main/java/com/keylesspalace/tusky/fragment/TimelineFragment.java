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
import android.support.annotation.NonNull;
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

import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.FooterViewHolder;
import com.keylesspalace.tusky.adapter.TimelineAdapter;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TimelineFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener,
        StatusActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "Timeline"; // logging tag
    private static final String KIND_ARG = "kind";
    private static final String HASHTAG_OR_ID_ARG = "hashtag_or_id";

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
    @Nullable
    private String bottomId;
    @Nullable
    private String topId;
    private PairedList<Status, StatusViewData> statuses =
            new PairedList<>(ViewDataUtils.statusMapper());

    public static TimelineFragment newInstance(Kind kind) {
        TimelineFragment fragment = new TimelineFragment();
        Bundle arguments = new Bundle();
        arguments.putString(KIND_ARG, kind.name());
        fragment.setArguments(arguments);
        return fragment;
    }

    public static TimelineFragment newInstance(Kind kind, String hashtagOrId) {
        TimelineFragment fragment = new TimelineFragment();
        Bundle arguments = new Bundle();
        arguments.putString(KIND_ARG, kind.name());
        arguments.putString(HASHTAG_OR_ID_ARG, hashtagOrId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        kind = Kind.valueOf(arguments.getString(KIND_ARG));
        if (kind == Kind.TAG || kind == Kind.USER) {
            hashtagOrId = arguments.getString(HASHTAG_OR_ID_ARG);
        }

        final View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        // Setup the SwipeRefreshLayout.
        Context context = getContext();
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Setup the RecyclerView.
        recyclerView = rootView.findViewById(R.id.recycler_view);
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

        timelineReceiver = new TimelineReceiver(this, this);
        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .registerReceiver(timelineReceiver, TimelineReceiver.getFilter(kind));

        statuses.clear();
        topLoading = false;
        topFetches = 0;
        bottomLoading = false;
        bottomFetches = 0;
        bottomId = null;
        topId = null;

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (jumpToTopAllowed()) {
            TabLayout layout = getActivity().findViewById(R.id.tab_layout);
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

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then. */
        if (actionButtonPresent()) {
            /* Use a modified scroll listener that both loads more statuses as it goes, and hides
             * the follow button on down-scroll. */
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            hideFab = preferences.getBoolean("fabHide", false);
            scrollListener = new EndlessOnScrollListener(layoutManager) {
                @Override
                public void onScrolled(RecyclerView view, int dx, int dy) {
                    super.onScrolled(view, dx, dy);

                    ActionButtonActivity activity = (ActionButtonActivity) getActivity();
                    FloatingActionButton composeButton = activity.getActionButton();

                    if (composeButton != null) {
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
                }

                @Override
                public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                    TimelineFragment.this.onLoadMore();
                }
            };
        } else {
            // Just use the basic scroll listener to load more statuses.
            scrollListener = new EndlessOnScrollListener(layoutManager) {
                @Override
                public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                    TimelineFragment.this.onLoadMore();
                }
            };
        }
        recyclerView.addOnScrollListener(scrollListener);
    }

    @Override
    public void onDestroyView() {
        if (jumpToTopAllowed()) {
            TabLayout tabLayout = getActivity().findViewById(R.id.tab_layout);
            tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        }
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(timelineReceiver);
        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        sendFetchTimelineRequest(null, topId, FetchEnd.TOP);
    }

    @Override
    public void onReply(int position) {
        super.reply(statuses.get(position));
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Status status = statuses.get(position);
        super.reblogWithCallback(status, reblog, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    status.reblogged = reblog;

                    if (status.reblog != null) {
                        status.reblog.reblogged = reblog;
                    }
                    StatusViewData newViewData =
                            new StatusViewData.Builder(statuses.getPairedItem(position))
                                    .setReblogged(reblog)
                                    .createStatusViewData();
                    statuses.setPairedItem(position, newViewData);
                    adapter.changeItem(position, newViewData, true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(TAG, "Failed to reblog status " + status.id);
                t.printStackTrace();
            }
        });
    }

    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Status status = statuses.get(position);

        super.favouriteWithCallback(status, favourite, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    status.favourited = favourite;

                    if (status.reblog != null) {
                        status.reblog.favourited = favourite;
                    }
                    StatusViewData newViewData = new StatusViewData
                            .Builder(statuses.getPairedItem(position))
                            .setFavourited(favourite)
                            .createStatusViewData();
                    statuses.setPairedItem(position, newViewData);
                    adapter.changeItem(position, newViewData, true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(TAG, "Failed to favourite status " + status.id);
                t.printStackTrace();
            }
        });
    }

    @Override
    public void onMore(View view, final int position) {
        super.more(statuses.get(position), view, position);
    }

    @Override
    public void onOpenReblog(int position) {
        super.openReblog(statuses.get(position));
    }

    @Override
    public void onExpandedChange(boolean expanded, int position) {
        StatusViewData newViewData = new StatusViewData.Builder(statuses.getPairedItem(position))
                .setIsExpanded(expanded).createStatusViewData();
        statuses.setPairedItem(position, newViewData);
        adapter.changeItem(position, newViewData, false);
    }

    @Override
    public void onContentHiddenChange(boolean isShowing, int position) {
        StatusViewData newViewData = new StatusViewData.Builder(statuses.getPairedItem(position))
                .setIsShowingSensitiveContent(isShowing).createStatusViewData();
        statuses.setPairedItem(position, newViewData);
        adapter.changeItem(position, newViewData, false);
    }

    @Override
    public void onViewMedia(String[] urls, int urlIndex, Status.MediaAttachment.Type type,
                            View view) {
        super.viewMedia(urls, urlIndex, type, view);
    }

    @Override
    public void onViewThread(int position) {
        super.viewThread(statuses.get(position));
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

    @Override
    public void removeItem(int position) {
        statuses.remove(position);
        adapter.update(statuses.getPairedCopy());
    }

    @Override
    public void removeAllByAccountId(String accountId) {
        // using iterator to safely remove items while iterating
        Iterator<Status> iterator = statuses.iterator();
        while (iterator.hasNext()) {
            Status status = iterator.next();
            if (status.account.id.equals(accountId)) {
                iterator.remove();
            }
        }
        adapter.update(statuses.getPairedCopy());
    }

    private void onLoadMore() {
        sendFetchTimelineRequest(bottomId, null, FetchEnd.BOTTOM);
    }

    private void fullyRefresh() {
        adapter.clear();
        sendFetchTimelineRequest(null, null, FetchEnd.TOP);
    }

    private boolean jumpToTopAllowed() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES;
    }

    private boolean actionButtonPresent() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES;
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
            case HOME:
                return api.homeTimeline(fromId, uptoId, null);
            case PUBLIC_FEDERATED:
                return api.publicTimeline(null, fromId, uptoId, null);
            case PUBLIC_LOCAL:
                return api.publicTimeline(true, fromId, uptoId, null);
            case TAG:
                return api.hashtagTimeline(tagOrId, null, fromId, uptoId, null);
            case USER:
                return api.accountStatuses(tagOrId, fromId, uptoId, null, null);
            case FAVOURITES:
                return api.favourites(fromId, uptoId, null);
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
            /* When this is called by the EndlessScrollListener it cannot refresh the footer state
             * using adapter.notifyItemChanged. So its necessary to postpone doing so until a
             * convenient time for the UI thread using a Runnable. */
            recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    adapter.setFooterState(FooterViewHolder.State.LOADING);
                }
            });
        }

        Callback<List<Status>> callback = new Callback<List<Status>>() {
            @Override
            public void onResponse(@NonNull Call<List<Status>> call, @NonNull Response<List<Status>> response) {
                if (response.isSuccessful()) {
                    String linkHeader = response.headers().get("Link");
                    onFetchTimelineSuccess(response.body(), linkHeader, fetchEnd);
                } else {
                    onFetchTimelineFailure(new Exception(response.message()), fetchEnd);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Status>> call, @NonNull Throwable t) {
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
                updateStatuses(statuses, null, uptoId);
                break;
            }
            case BOTTOM: {
                HttpHeaderLink next = HttpHeaderLink.findByRelationType(links, "next");
                String fromId = null;
                if (next != null) {
                    fromId = next.uri.getQueryParameter("max_id");
                }
                if (adapter.getItemCount() > 1) {
                    addItems(statuses, fromId);
                } else {
                    /* If this is the first fetch, also save the id from the "previous" link and
                     * treat this operation as a refresh so the scroll position doesn't get pushed
                     * down to the end. */
                    HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                    String uptoId = null;
                    if (previous != null) {
                        uptoId = previous.uri.getQueryParameter("since_id");
                    }
                    updateStatuses(statuses, fromId, uptoId);
                }
                break;
            }
        }
        fulfillAnyQueuedFetches(fetchEnd);
        if (statuses.size() == 0 && adapter.getItemCount() == 1) {
            adapter.setFooterState(FooterViewHolder.State.EMPTY);
        } else {
            adapter.setFooterState(FooterViewHolder.State.END);
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
                    onLoadMore();
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

    private void updateStatuses(List<Status> newStatuses, @Nullable String fromId,
                                @Nullable String toId) {
        if (ListUtils.isEmpty(newStatuses)) {
            return;
        }
        if (fromId != null) {
            bottomId = fromId;
        }
        if (toId != null) {
            topId = toId;
        }
        if (statuses.isEmpty()) {
            // This construction removes duplicates while preserving order.
            statuses.addAll(new LinkedHashSet<>(newStatuses));
        } else {
            Status lastOfNew = newStatuses.get(newStatuses.size() - 1);
            int index = statuses.indexOf(lastOfNew);
            for (int i = 0; i < index; i++) {
                statuses.remove(0);
            }
            int newIndex = newStatuses.indexOf(statuses.get(0));
            if (newIndex == -1) {
                statuses.addAll(0, newStatuses);
            } else {
                statuses.addAll(0, newStatuses.subList(0, newIndex));
            }
        }
        adapter.update(statuses.getPairedCopy());
    }

    private void addItems(List<Status> newStatuses, @Nullable String fromId) {
        if (ListUtils.isEmpty(newStatuses)) {
            return;
        }
        int end = statuses.size();
        Status last = statuses.get(end - 1);
        if (last != null && !findStatus(newStatuses, last.id)) {
            statuses.addAll(newStatuses);
            List<StatusViewData> newViewDatas = statuses.getPairedCopy()
                    .subList(statuses.size() - newStatuses.size(), statuses.size());
            if (BuildConfig.DEBUG && newStatuses.size() != newViewDatas.size()) {
                String error = String.format(Locale.getDefault(),
                        "Incorrectly got statusViewData sublist." +
                        " newStatuses.size == %d newViewDatas.size == %d, statuses.size == %d",
                        newStatuses.size(), newViewDatas.size(), statuses.size());
                throw new AssertionError(error);
            }
            if (fromId != null) {
                bottomId = fromId;
            }
            adapter.addItems(newViewDatas);
        }
    }

    private static boolean findStatus(List<Status> statuses, String id) {
        for (Status status : statuses) {
            if (status.id.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
