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
import android.support.v4.content.ContextCompat;
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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener, FooterActionListener {
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;

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
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.status_divider);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        EndlessOnScrollListener scrollListener = new EndlessOnScrollListener(layoutManager) {
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

        sendFetchNotificationsRequest();

        return rootView;
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
                        List<Notification> notifications = new ArrayList<>();
                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject object = response.getJSONObject(i);
                                String id = object.getString("id");
                                Notification.Type type = Notification.Type.valueOf(
                                        object.getString("type").toUpperCase());
                                JSONObject account = object.getJSONObject("account");
                                String displayName = account.getString("display_name");
                                Notification notification = new Notification(type, id, displayName);
                                if (notification.hasStatusType()) {
                                    JSONObject statusObject = object.getJSONObject("status");
                                    Status status = Status.parse(statusObject, false);
                                    notification.setStatus(status);
                                }
                                notifications.add(notification);
                            }
                            onFetchNotificationsSuccess(notifications, fromId != null);
                        } catch (JSONException e) {
                            onFetchNotificationsFailure();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onFetchNotificationsFailure();
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

    private void sendFetchNotificationsRequest() {
        sendFetchNotificationsRequest(null);
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, boolean added) {
        if (added) {
            adapter.addItems(notifications);
        } else {
            adapter.update(notifications);
        }
        showFetchTimelineRetry(false);
        swipeRefreshLayout.setRefreshing(false);
    }

    private void onFetchNotificationsFailure() {
        showFetchTimelineRetry(true);
        swipeRefreshLayout.setRefreshing(false);
    }

    private void showFetchTimelineRetry(boolean show) {
        RecyclerView.ViewHolder viewHolder =
                recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
        if (viewHolder != null) {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.showRetry(show);
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
}
