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

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener, FooterActionListener {
    private static final String TAG = "Notifications"; // logging tag and Volley request tag

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private NotificationsAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;

    public static NotificationsFragment newInstance() {
        NotificationsFragment fragment = new NotificationsFragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
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
        scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                NotificationsAdapter adapter = (NotificationsAdapter) view.getAdapter();
                Notification notification = adapter.getItem(adapter.getItemCount() - 2);
                if (notification != null) {
                    sendFetchNotificationsRequest(notification.getId());
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

        sendFetchNotificationsRequest();

        return rootView;
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

    private void sendFetchNotificationsRequest(final String fromId) {
        String endpoint = getString(R.string.endpoint_notifications);
        String url = "https://" + domain + endpoint;
        if (fromId != null) {
            url += "?max_id=" + fromId;
        }
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            List<Notification> notifications = Notification.parse(response);
                            onFetchNotificationsSuccess(notifications, fromId);
                        } catch (JSONException e) {
                            onFetchNotificationsFailure(e);
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onFetchNotificationsFailure(error);
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

    private void sendFetchNotificationsRequest() {
        sendFetchNotificationsRequest(null);
    }

    private static boolean findNotification(List<Notification> notifications, String id) {
        for (Notification notification : notifications) {
            if (notification.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, String fromId) {
        if (fromId != null) {
            if (notifications.size() > 0 && !findNotification(notifications, fromId)) {
                setFetchTimelineState(FooterViewHolder.State.LOADING);
                adapter.addItems(notifications);
            } else {
                setFetchTimelineState(FooterViewHolder.State.END_OF_TIMELINE);
            }
        } else {
            adapter.update(notifications);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void onFetchNotificationsFailure(Exception exception) {
        setFetchTimelineState(FooterViewHolder.State.RETRY);
        swipeRefreshLayout.setRefreshing(false);
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
    }

    private void setFetchTimelineState(FooterViewHolder.State state) {
        adapter.setFooterState(state);
        RecyclerView.ViewHolder viewHolder =
                recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
        if (viewHolder != null) {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(state);
        }
    }

    public void onRefresh() {
        sendFetchNotificationsRequest();
    }

    public void onLoadMore() {
        Notification notification = adapter.getItem(adapter.getItemCount() - 2);
        if (notification != null) {
            sendFetchNotificationsRequest(notification.getId());
        } else {
            sendFetchNotificationsRequest();
        }
    }

    public void onReply(int position) {
        Notification notification = adapter.getItem(position);
        super.reply(notification.getStatus());
    }

    public void onReblog(boolean reblog, int position) {
        Notification notification = adapter.getItem(position);
        super.reblog(notification.getStatus(), reblog, adapter, position);
    }

    public void onFavourite(boolean favourite, int position) {
        Notification notification = adapter.getItem(position);
        super.favourite(notification.getStatus(), favourite, adapter, position);
    }

    public void onMore(View view, int position) {
        Notification notification = adapter.getItem(position);
        super.more(notification.getStatus(), view, adapter, position);
    }

    public void onViewMedia(String url, Status.MediaAttachment.Type type) {
        super.viewMedia(url, type);
    }

    public void onViewThread(int position) {
        Notification notification = adapter.getItem(position);
        super.viewThread(notification.getStatus());
    }

    public void onViewTag(String tag) {
        super.viewTag(tag);
    }

    public void onViewAccount(String id, String username) {
        super.viewAccount(id, username);
    }

    public void onViewAccount(int position) {
        Status status = adapter.getItem(position).getStatus();
        String id = status.getAccountId();
        String username = status.getUsername();
        super.viewAccount(id, username);
    }
}
