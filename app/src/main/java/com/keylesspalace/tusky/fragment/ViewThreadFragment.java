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

import android.arch.core.util.Function;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
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
import com.keylesspalace.tusky.adapter.ThreadAdapter;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Card;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.entity.StatusContext;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.ConversationLineItemDecoration;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViewThreadFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener {
    private static final String TAG = "ViewThreadFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ThreadAdapter adapter;
    private String thisThreadsStatusId;
    private TimelineReceiver timelineReceiver;
    private Card card;
    private boolean alwaysShowSensitiveMedia;

    private int statusIndex = 0;

    private PairedList<Status, StatusViewData.Concrete> statuses =
            new PairedList<>(new Function<Status, StatusViewData.Concrete>() {
                @Override
                public StatusViewData.Concrete apply(Status input) {
                    return ViewDataUtils.statusToViewData(input, alwaysShowSensitiveMedia);
                }
            });

    public static ViewThreadFragment newInstance(String id) {
        Bundle arguments = new Bundle();
        ViewThreadFragment fragment = new ViewThreadFragment();
        arguments.putString("id", id);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_view_thread, container, false);

        Context context = getContext();
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);

        recyclerView = rootView.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable dividerDrawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(dividerDrawable);
        recyclerView.addItemDecoration(divider);

        Drawable threadLineDrawable = ThemeUtils.getDrawable(context, R.attr.conversation_thread_line_drawable,
                R.drawable.conversation_thread_line_dark);
        recyclerView.addItemDecoration(new ConversationLineItemDecoration(context,
                threadLineDrawable));
        adapter = new ThreadAdapter(this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());
        alwaysShowSensitiveMedia = preferences.getBoolean("alwaysShowSensitiveMedia", false);
        boolean mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true);
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        recyclerView.setAdapter(adapter);

        statuses.clear();
        thisThreadsStatusId = null;

        timelineReceiver = new TimelineReceiver(this, this);
        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .registerReceiver(timelineReceiver, TimelineReceiver.getFilter(null));

        return rootView;
    }

    @Override
    public void onDestroyView() {
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(timelineReceiver);
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        thisThreadsStatusId = getArguments().getString("id");
        onRefresh();
    }

    @Override
    public void onRefresh() {
        sendStatusRequest(thisThreadsStatusId);
        sendThreadRequest(thisThreadsStatusId);
        sendCardRequest(thisThreadsStatusId);
    }

    @Override
    public void onReply(int position) {
        super.reply(statuses.get(position));
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Status status = statuses.get(position);
        super.reblogWithCallback(statuses.get(position), reblog, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull Response<Status> response) {
                if (response.isSuccessful()) {
                    status.reblogged = reblog;

                    if (status.reblog != null) {
                        status.reblog.reblogged = reblog;
                    }

                    StatusViewData.Concrete viewdata = statuses.getPairedItem(position);

                    StatusViewData.Builder viewDataBuilder = new StatusViewData.Builder((viewdata));
                    viewDataBuilder.setReblogged(reblog);

                    StatusViewData.Concrete newViewData = viewDataBuilder.createStatusViewData();

                    statuses.setPairedItem(position, newViewData);
                    adapter.setItem(position, newViewData, true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(getClass().getSimpleName(), "Failed to reblog status: " + status.id);
                t.printStackTrace();
            }
        });
    }

    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Status status = statuses.get(position);
        super.favouriteWithCallback(statuses.get(position), favourite, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull Response<Status> response) {
                if (response.isSuccessful()) {
                    status.favourited = favourite;

                    if (status.reblog != null) {
                        status.reblog.favourited = favourite;
                    }

                    StatusViewData.Concrete viewdata = statuses.getPairedItem(position);

                    StatusViewData.Builder viewDataBuilder = new StatusViewData.Builder((viewdata));
                    viewDataBuilder.setFavourited(favourite);

                    StatusViewData.Concrete newViewData = viewDataBuilder.createStatusViewData();

                    statuses.setPairedItem(position, newViewData);
                    adapter.setItem(position, newViewData, true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(getClass().getSimpleName(), "Failed to favourite status: " + status.id);
                t.printStackTrace();
            }
        });
    }

    @Override
    public void onMore(View view, int position) {
        super.more(statuses.get(position), view, position);
    }

    @Override
    public void onViewMedia(String[] urls, int urlIndex, Attachment.Type type,
                            View view) {
        super.viewMedia(urls, urlIndex, type, view);
    }

    @Override
    public void onViewThread(int position) {
        Status status = statuses.get(position);
        if (thisThreadsStatusId.equals(status.id)) {
            // If already viewing this thread, don't reopen it.
            return;
        }
        super.viewThread(status);
    }

    @Override
    public void onOpenReblog(int position) {
        // there should be no reblogs in the thread but let's implement it to be sure
        super.openReblog(statuses.get(position));
    }

    @Override
    public void onExpandedChange(boolean expanded, int position) {
        StatusViewData.Concrete newViewData =
                new StatusViewData.Builder(statuses.getPairedItem(position))
                        .setIsExpanded(expanded)
                        .createStatusViewData();
        statuses.setPairedItem(position, newViewData);
        adapter.setItem(position, newViewData, false);
    }

    @Override
    public void onContentHiddenChange(boolean isShowing, int position) {
        StatusViewData.Concrete newViewData =
                new StatusViewData.Builder(statuses.getPairedItem(position))
                        .setIsShowingSensitiveContent(isShowing)
                        .createStatusViewData();
        statuses.setPairedItem(position, newViewData);
        adapter.setItem(position, newViewData, false);
    }

    @Override
    public void onLoadMore(int pos) {

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
    public void removeItem(int position) {
        if (position == statusIndex) {
            //the status got removed, close the activity
            getActivity().finish();
        }
        statuses.remove(position);
        adapter.setStatuses(statuses.getPairedCopy());
    }

    @Override
    public void removeAllByAccountId(String accountId) {
        Status status = null;
        if (!statuses.isEmpty()) {
            status = statuses.get(statusIndex);
        }
        // using iterator to safely remove items while iterating
        Iterator<Status> iterator = statuses.iterator();
        while (iterator.hasNext()) {
            Status s = iterator.next();
            if (s.account.id.equals(accountId)) {
                iterator.remove();
            }
        }
        statusIndex = statuses.indexOf(status);
        if (statusIndex == -1) {
            //the status got removed, close the activity
            getActivity().finish();
            return;
        }
        adapter.setDetailedStatusPosition(statusIndex);
        adapter.setStatuses(statuses.getPairedCopy());
    }

    private void sendStatusRequest(final String id) {
        Call<Status> call = mastodonApi.status(id);
        call.enqueue(new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull Response<Status> response) {
                if (response.isSuccessful()) {
                    int position = setStatus(response.body());
                    recyclerView.scrollToPosition(position);
                } else {
                    onThreadRequestFailure(id);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                onThreadRequestFailure(id);
            }
        });
        callList.add(call);
    }

    private void sendThreadRequest(final String id) {
        Call<StatusContext> call = mastodonApi.statusContext(id);
        call.enqueue(new Callback<StatusContext>() {
            @Override
            public void onResponse(@NonNull Call<StatusContext> call, @NonNull Response<StatusContext> response) {
                StatusContext context = response.body();
                if (response.isSuccessful() && context != null) {
                    swipeRefreshLayout.setRefreshing(false);
                    setContext(context.ancestors, context.descendants);
                } else {
                    onThreadRequestFailure(id);
                }
            }

            @Override
            public void onFailure(@NonNull Call<StatusContext> call, @NonNull Throwable t) {
                onThreadRequestFailure(id);
            }
        });
        callList.add(call);
    }

    private void sendCardRequest(final String id) {
        Call<Card> call = mastodonApi.statusCard(id);
        call.enqueue(new Callback<Card>() {
            @Override
            public void onResponse(@NonNull Call<Card> call, @NonNull Response<Card> response) {
                if (response.isSuccessful()) {
                    showCard(response.body());
                } else {
                    onThreadRequestFailure(id);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Card> call, @NonNull Throwable t) {
                onThreadRequestFailure(id);
            }
        });
        callList.add(call);
    }

    private void onThreadRequestFailure(final String id) {
        View view = getView();
        swipeRefreshLayout.setRefreshing(false);
        if (view != null) {
            Snackbar.make(view, R.string.error_generic, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_retry, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sendThreadRequest(id);
                            sendStatusRequest(id);
                            sendCardRequest(id);
                        }
                    })
                    .show();
        } else {
            Log.e(TAG, "Couldn't display thread fetch error message");
        }
    }

    private int setStatus(Status status) {
        if (statuses.size() > 0
                && statusIndex < statuses.size()
                && statuses.get(statusIndex).equals(status)) {
            // Do not add this status on refresh, it's already in there.
            statuses.set(statusIndex, status);
            return statusIndex;
        }
        int i = statusIndex;
        statuses.add(i, status);
        adapter.setDetailedStatusPosition(i);
        StatusViewData.Concrete viewData = statuses.getPairedItem(i);
        if (viewData.getCard() == null && card != null) {
            viewData = new StatusViewData.Builder(viewData)
                    .setCard(card)
                    .createStatusViewData();
        }
        statuses.setPairedItem(i, viewData);
        adapter.addItem(i, viewData);
        return i;
    }

    private void setContext(List<Status> ancestors, List<Status> descendants) {
        Status mainStatus = null;

        // In case of refresh, remove old ancestors and descendants first. We'll remove all blindly,
        // as we have no guarantee on their order to be the same as before
        int oldSize = statuses.size();
        if (oldSize > 1) {
            mainStatus = statuses.get(statusIndex);
            statuses.clear();
            adapter.clearItems();
        }

        // Insert newly fetched ancestors
        statusIndex = ancestors.size();
        adapter.setDetailedStatusPosition(statusIndex);
        statuses.addAll(0, ancestors);
        List<StatusViewData.Concrete> ancestorsViewDatas = statuses.getPairedCopy().subList(0, statusIndex);
        if (BuildConfig.DEBUG && ancestors.size() != ancestorsViewDatas.size()) {
            String error = String.format(Locale.getDefault(),
                    "Incorrectly got statusViewData sublist." +
                            " ancestors.size == %d ancestorsViewDatas.size == %d," +
                            " statuses.size == %d",
                    ancestors.size(), ancestorsViewDatas.size(), statuses.size());
            throw new AssertionError(error);
        }
        adapter.addAll(0, ancestorsViewDatas);

        if (mainStatus != null) {
            // In case we needed to delete everything (which is way easier than deleting
            // everything except one), re-insert the remaining status here.
            statuses.add(statusIndex, mainStatus);
            StatusViewData.Concrete viewData = statuses.getPairedItem(statusIndex);
            if (viewData.getCard() == null && card != null) {
                viewData = new StatusViewData.Builder(viewData)
                        .setCard(card)
                        .createStatusViewData();
                statuses.setPairedItem(statusIndex, viewData);

            }
            adapter.addItem(statusIndex, viewData);
        }

        // Insert newly fetched descendants
        statuses.addAll(descendants);
        List<StatusViewData.Concrete> descendantsViewData;
        descendantsViewData = statuses.getPairedCopy()
                .subList(statuses.size() - descendants.size(), statuses.size());
        if (BuildConfig.DEBUG && descendants.size() != descendantsViewData.size()) {
            String error = String.format(Locale.getDefault(),
                    "Incorrectly got statusViewData sublist." +
                            " descendants.size == %d descendantsViewData.size == %d," +
                            " statuses.size == %d",
                    descendants.size(), descendantsViewData.size(), statuses.size());
            throw new AssertionError(error);
        }
        adapter.addAll(descendantsViewData);
    }

    private void showCard(Card card) {
        this.card = card;
        if (statusIndex >= 0 && statusIndex < statuses.size()) {
            StatusViewData.Concrete newViewData =
                    new StatusViewData.Builder(statuses.getPairedItem(statusIndex))
                            .setCard(card)
                            .createStatusViewData();

            statuses.setPairedItem(statusIndex, newViewData);
            adapter.setItem(statusIndex, newViewData, true);
        }
    }

    public void clear() {
        statuses.clear();
        adapter.clear();
    }
}
