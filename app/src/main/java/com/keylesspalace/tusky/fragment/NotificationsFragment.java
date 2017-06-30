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
import com.keylesspalace.tusky.adapter.NotificationsAdapter;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener,
        NotificationsAdapter.NotificationActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "Notifications"; // logging tag

    private enum FetchEnd {
        TOP,
        BOTTOM,
    }

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager layoutManager;
    private RecyclerView recyclerView;
    private EndlessOnScrollListener scrollListener;
    private NotificationsAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private boolean hideFab;
    private TimelineReceiver timelineReceiver;
    private boolean topLoading;
    private int topFetches;
    private boolean bottomLoading;
    private int bottomFetches;

    public static NotificationsFragment newInstance() {
        NotificationsFragment fragment = new NotificationsFragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

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

        adapter = new NotificationsAdapter(this, this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());
        boolean mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true);
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        recyclerView.setAdapter(adapter);

        timelineReceiver = new TimelineReceiver(adapter);
        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .registerReceiver(timelineReceiver, TimelineReceiver.getFilter(null));

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        MainActivity activity = (MainActivity) getActivity();

        // MainActivity's layout is guaranteed to be inflated until onCreate returns.
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

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then.
         * Use a modified scroll listener that both loads more notifications as it goes, and hides
         * the compose button on down-scroll. */
        final FloatingActionButton composeButton = activity.composeButton;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                activity);
        preferences.registerOnSharedPreferenceChangeListener(this);
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
                NotificationsFragment.this.onLoadMore(view);
            }
        };

        recyclerView.addOnScrollListener(scrollListener);
    }

    @Override
    public void onDestroyView() {
        TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
        tabLayout.removeOnTabSelectedListener(onTabSelectedListener);

        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(timelineReceiver);

        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        sendFetchNotificationsRequest(null, adapter.getTopId(), FetchEnd.TOP);
    }

    @Override
    public void onReply(int position) {
        Notification notification = adapter.getItem(position);
        super.reply(notification.status);
    }

    @Override
    public void onReblog(boolean reblog, int position) {
        Notification notification = adapter.getItem(position);
        super.reblog(notification.status, reblog, adapter, position);
    }

    @Override
    public void onFavourite(boolean favourite, int position) {
        Notification notification = adapter.getItem(position);
        super.favourite(notification.status, favourite, adapter, position);
    }

    @Override
    public void onMore(View view, int position) {
        Notification notification = adapter.getItem(position);
        super.more(notification.status, view, adapter, position);
    }

    @Override
    public void onViewMedia(String[] urls, int urlIndex, Status.MediaAttachment.Type type) {
        super.viewMedia(urls, urlIndex, type);
    }

    @Override
    public void onViewThread(int position) {
        Notification notification = adapter.getItem(position);
        super.viewThread(notification.status);
    }

    @Override
    public void onViewTag(String tag) {
        super.viewTag(tag);
    }

    @Override
    public void onViewAccount(String id) {
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
        }
    }

    private void onLoadMore(RecyclerView view) {
        NotificationsAdapter adapter = (NotificationsAdapter) view.getAdapter();
        sendFetchNotificationsRequest(adapter.getBottomId(), null, FetchEnd.BOTTOM);
    }

    private void jumpToTop() {
        layoutManager.scrollToPosition(0);
        scrollListener.reset();
    }

    private void sendFetchNotificationsRequest(String fromId, String uptoId,
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
            adapter.setFooterState(NotificationsAdapter.FooterState.LOADING);
        }

        Call<List<Notification>> call = mastodonApi.notifications(fromId, uptoId, null);

        call.enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call,
                    Response<List<Notification>> response) {
                if (response.isSuccessful()) {
                    String linkHeader = response.headers().get("Link");
                    onFetchNotificationsSuccess(response.body(), linkHeader, fetchEnd);
                } else {
                    onFetchNotificationsFailure(new Exception(response.message()), fetchEnd);
                }
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable t) {
                onFetchNotificationsFailure((Exception) t, fetchEnd);
            }
        });
        callList.add(call);
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, String linkHeader,
            FetchEnd fetchEnd) {
        List<HttpHeaderLink> links = HttpHeaderLink.parse(linkHeader);
        switch (fetchEnd) {
            case TOP: {
                HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                String uptoId = null;
                if (previous != null) {
                    uptoId = previous.uri.getQueryParameter("since_id");
                }
                adapter.update(notifications, null, uptoId);
                break;
            }
            case BOTTOM: {
                HttpHeaderLink next = HttpHeaderLink.findByRelationType(links, "next");
                String fromId = null;
                if (next != null) {
                    fromId = next.uri.getQueryParameter("max_id");
                }
                if (adapter.getItemCount() > 1) {
                    adapter.addItems(notifications, fromId);
                } else {
                    /* If this is the first fetch, also save the id from the "previous" link and
                     * treat this operation as a refresh so the scroll position doesn't get pushed
                     * down to the end. */
                    HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                    String uptoId = null;
                    if (previous != null) {
                        uptoId = previous.uri.getQueryParameter("since_id");
                    }
                    adapter.update(notifications, fromId, uptoId);
                }
                /* Set last update id for pull notifications so that we don't get notified
                 * about things we already loaded here */
                getPrivatePreferences().edit()
                        .putString("lastUpdateId", fromId)
                        .apply();
                break;
            }
        }
        fulfillAnyQueuedFetches(fetchEnd);
        if (notifications.size() == 0 && adapter.getItemCount() == 1) {
            adapter.setFooterState(NotificationsAdapter.FooterState.EMPTY);
        } else {
            adapter.setFooterState(NotificationsAdapter.FooterState.END);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void onFetchNotificationsFailure(Exception exception, FetchEnd fetchEnd) {
        swipeRefreshLayout.setRefreshing(false);
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
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

    private void fullyRefresh() {
        adapter.clear();
        sendFetchNotificationsRequest(null, null, FetchEnd.TOP);
    }
}
