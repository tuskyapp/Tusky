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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.Request;
import com.android.volley.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ViewThreadFragment extends SFragment implements StatusActionListener {
    private static final String TAG = "ViewThread"; // logging tag

    private RecyclerView recyclerView;
    private ThreadAdapter adapter;

    public static ViewThreadFragment newInstance(String id) {
        Bundle arguments = new Bundle();
        ViewThreadFragment fragment = new ViewThreadFragment();
        arguments.putString("id", id);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_view_thread, container, false);

        Context context = getContext();
        recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        adapter = new ThreadAdapter(this);
        recyclerView.setAdapter(adapter);

        String id = getArguments().getString("id");
        sendStatusRequest(id);
        sendThreadRequest(id);

        return rootView;
    }

    private void sendStatusRequest(String id) {
        String endpoint = String.format(getString(R.string.endpoint_get_status), id);
        super.sendRequest(Request.Method.GET, endpoint, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Status status;
                        try {
                            status = Status.parse(response, false);
                        } catch (JSONException e) {
                            onThreadRequestFailure();
                            return;
                        }
                        int position = adapter.insertStatus(status);
                        recyclerView.scrollToPosition(position);
                    }
                });
    }

    private void sendThreadRequest(String id) {
        String endpoint = String.format(getString(R.string.endpoint_context), id);
        super.sendRequest(Request.Method.GET, endpoint, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            List<Status> ancestors =
                                    Status.parse(response.getJSONArray("ancestors"));
                            List<Status> descendants =
                                    Status.parse(response.getJSONArray("descendants"));
                            adapter.addAncestors(ancestors);
                            adapter.addDescendants(descendants);
                        } catch (JSONException e) {
                            onThreadRequestFailure();
                        }
                    }
                });
    }

    private void onThreadRequestFailure() {
        Log.e(TAG, "The request to fetch the thread has failed.");
        //TODO: no
    }

    public void onReply(int position) {
        super.reply(adapter.getItem(position));
    }

    public void onReblog(boolean reblog, int position) {
        super.reblog(adapter.getItem(position), reblog, adapter, position);
    }

    public void onFavourite(boolean favourite, int position) {
        super.favourite(adapter.getItem(position), favourite, adapter, position);
    }

    public void onMore(View view, int position) {
        super.more(adapter.getItem(position), view, adapter, position);
    }

    public void onViewMedia(String url, Status.MediaAttachment.Type type) {
        super.viewMedia(url, type);
    }

    public void onViewThread(int position) {
        super.viewThread(adapter.getItem(position));
    }

    public void onViewTag(String tag) {
        super.viewTag(tag);
    }

    public void onViewAccount(String id, String username) {
        super.viewAccount(id, username);
    }

    public void onViewAccount(int position) {
        Status status = adapter.getItem(position);
        String id = status.getAccountId();
        String username = status.getUsername();
        super.viewAccount(id, username);
    }
}
