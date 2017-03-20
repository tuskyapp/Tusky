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
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener,
        NotificationsAdapter.NotificationActionListener {
    private static final String TAG = "Notifications"; // logging tag

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private NotificationsAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private Call<List<Notification>> listCall;

    public static NotificationsFragment newInstance() {
        NotificationsFragment fragment = new NotificationsFragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                NotificationsAdapter adapter = (NotificationsAdapter) view.getAdapter();
                Notification notification = adapter.getItem(adapter.getItemCount() - 2);
                if (notification != null) {
                    sendFetchNotificationsRequest(notification.id, null);
                } else {
                    sendFetchNotificationsRequest();
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
        adapter = new NotificationsAdapter(this, this);
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
    public void onResume() {
        super.onResume();
        sendFetchNotificationsRequest();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listCall != null) listCall.cancel();
    }

    @Override
    public void onDestroyView() {
        TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
        tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        super.onDestroyView();
    }

    private void jumpToTop() {
        layoutManager.scrollToPosition(0);
        scrollListener.reset();
    }

    private void sendFetchNotificationsRequest(final String fromId, String uptoId) {
        MastodonAPI api = ((BaseActivity) getActivity()).mastodonAPI;

        listCall = api.notifications(fromId, uptoId, null);

        listCall.enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call, Response<List<Notification>> response) {
                if (response.isSuccessful()) {
                    onFetchNotificationsSuccess(response.body(), fromId);
                } else {
                    onFetchNotificationsFailure(new Exception(response.message()));
                }
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable t) {
                onFetchNotificationsFailure((Exception) t);
            }
        });
        callList.add(listCall);
    }

    private void sendFetchNotificationsRequest() {
        sendFetchNotificationsRequest(null, null);
    }

    private static boolean findNotification(List<Notification> notifications, String id) {
        for (Notification notification : notifications) {
            if (notification.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, String fromId) {
        if (fromId != null) {
            if (notifications.size() > 0 && !findNotification(notifications, fromId)) {
                adapter.addItems(notifications);

                // Set last update id for pull notifications so that we don't get notified
                // about things we already loaded here
                SharedPreferences preferences = getActivity().getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("lastUpdateId", notifications.get(0).id);
                editor.apply();
            }
        } else {
            adapter.update(notifications);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void onFetchNotificationsFailure(Exception exception) {
        swipeRefreshLayout.setRefreshing(false);
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
    }

    public void onRefresh() {
        Notification notification = adapter.getItem(0);
        if (notification != null) {
            sendFetchNotificationsRequest(null, notification.id);
        } else {
            sendFetchNotificationsRequest();
        }
    }

    public void onReply(int position) {
        Notification notification = adapter.getItem(position);
        super.reply(notification.status);
    }

    public void onReblog(boolean reblog, int position) {
        Notification notification = adapter.getItem(position);
        super.reblog(notification.status, reblog, adapter, position);
    }

    public void onFavourite(boolean favourite, int position) {
        Notification notification = adapter.getItem(position);
        super.favourite(notification.status, favourite, adapter, position);
    }

    public void onMore(View view, int position) {
        Notification notification = adapter.getItem(position);
        super.more(notification.status, view, adapter, position);
    }

    public void onViewMedia(String url, Status.MediaAttachment.Type type) {
        super.viewMedia(url, type);
    }

    public void onViewThread(int position) {
        Notification notification = adapter.getItem(position);
        super.viewThread(notification.status);
    }

    public void onViewTag(String tag) {
        super.viewTag(tag);
    }

    public void onViewAccount(String id) {
        super.viewAccount(id);
    }
}
