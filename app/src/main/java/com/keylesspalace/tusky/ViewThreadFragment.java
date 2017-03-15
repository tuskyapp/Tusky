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
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.entity.StatusContext;

import retrofit2.Call;
import retrofit2.Callback;

public class ViewThreadFragment extends SFragment implements StatusActionListener {
    private RecyclerView recyclerView;
    private ThreadAdapter adapter;
    private String thisThreadsStatusId;

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
        Drawable drawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        recyclerView.addItemDecoration(new ConversationLineItemDecoration(context, ContextCompat.getDrawable(context, R.drawable.conversation_divider_dark)));
        adapter = new ThreadAdapter(this);
        recyclerView.setAdapter(adapter);

        String id = getArguments().getString("id");
        sendStatusRequest(id);
        sendThreadRequest(id);
        thisThreadsStatusId = id;

        return rootView;
    }

    private void sendStatusRequest(final String id) {
        MastodonAPI api = ((BaseActivity) getActivity()).mastodonAPI;

        Call<Status> call = api.status(id);
        call.enqueue(new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    int position = adapter.insertStatus(response.body());
                    recyclerView.scrollToPosition(position);
                } else {
                    onThreadRequestFailure(id);
                }
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {
                onThreadRequestFailure(id);
            }
        });
        callList.add(call);
    }

    private void sendThreadRequest(final String id) {
        MastodonAPI api = ((BaseActivity) getActivity()).mastodonAPI;

        Call<StatusContext> call = api.statusContext(id);
        call.enqueue(new Callback<StatusContext>() {
            @Override
            public void onResponse(Call<StatusContext> call, retrofit2.Response<StatusContext> response) {
                if (response.isSuccessful()) {
                    StatusContext context = response.body();

                    adapter.addAncestors(context.ancestors);
                    adapter.addDescendants(context.descendants);
                } else {
                    onThreadRequestFailure(id);
                }
            }

            @Override
            public void onFailure(Call<StatusContext> call, Throwable t) {
                onThreadRequestFailure(id);
            }
        });
        callList.add(call);
    }

    private void onThreadRequestFailure(final String id) {
        Snackbar.make(recyclerView, R.string.error_view_thread, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendThreadRequest(id);
                        sendStatusRequest(id);
                    }
                })
                .show();
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
        Status status = adapter.getItem(position);
        if (thisThreadsStatusId.equals(status.id)) {
            // If already viewing this thread, don't reopen it.
            return;
        }
        super.viewThread(status);
    }

    public void onViewTag(String tag) {
        super.viewTag(tag);
    }

    public void onViewAccount(String id) {
        super.viewAccount(id);
    }
}
