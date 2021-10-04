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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.keylesspalace.tusky.AccountListActivity;
import com.keylesspalace.tusky.BaseActivity;
import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ViewThreadActivity;
import com.keylesspalace.tusky.adapter.ThreadAdapter;
import com.keylesspalace.tusky.appstore.BlockEvent;
import com.keylesspalace.tusky.appstore.BookmarkEvent;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.FavoriteEvent;
import com.keylesspalace.tusky.appstore.PinEvent;
import com.keylesspalace.tusky.appstore.ReblogEvent;
import com.keylesspalace.tusky.appstore.StatusComposedEvent;
import com.keylesspalace.tusky.appstore.StatusDeletedEvent;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Filter;
import com.keylesspalace.tusky.entity.Poll;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.network.FilterModel;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.settings.PrefKeys;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.ConversationLineItemDecoration;
import com.keylesspalace.tusky.viewdata.AttachmentViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import kotlin.collections.CollectionsKt;

import static autodispose2.AutoDispose.autoDisposable;
import static autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider.from;

public final class ViewThreadFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener, Injectable {
    private static final String TAG = "ViewThreadFragment";

    @Inject
    public MastodonApi mastodonApi;
    @Inject
    public EventHub eventHub;
    @Inject
    public FilterModel filterModel;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ThreadAdapter adapter;
    private String thisThreadsStatusId;
    private boolean alwaysShowSensitiveMedia;
    private boolean alwaysOpenSpoiler;

    private int statusIndex = 0;

    private final PairedList<Status, StatusViewData.Concrete> statuses =
            new PairedList<>(new Function<Status, StatusViewData.Concrete>() {
                @Override
                public StatusViewData.Concrete apply(Status input) {
                    return ViewDataUtils.statusToViewData(
                            input,
                            alwaysShowSensitiveMedia,
                            alwaysOpenSpoiler
                    );
                }
            });

    public static ViewThreadFragment newInstance(String id) {
        Bundle arguments = new Bundle(1);
        ViewThreadFragment fragment = new ViewThreadFragment();
        arguments.putString("id", id);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        thisThreadsStatusId = getArguments().getString("id");
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        StatusDisplayOptions statusDisplayOptions = new StatusDisplayOptions(
                preferences.getBoolean("animateGifAvatars", false),
                accountManager.getActiveAccount().getMediaPreviewEnabled(),
                preferences.getBoolean("absoluteTimeView", false),
                preferences.getBoolean("showBotOverlay", true),
                preferences.getBoolean("useBlurhash", true),
                preferences.getBoolean("showCardsInTimelines", false) ?
                        CardViewMode.INDENTED :
                        CardViewMode.NONE,
                preferences.getBoolean("confirmReblogs", true),
                preferences.getBoolean("confirmFavourites", true),
                preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
                preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        );
        adapter = new ThreadAdapter(statusDisplayOptions, this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_view_thread, container, false);

        Context context = getContext();
        swipeRefreshLayout = rootView.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue);

        recyclerView = rootView.findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAccessibilityDelegateCompat(
                new ListStatusAccessibilityDelegate(recyclerView, this, statuses::getPairedItemOrNull));
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        recyclerView.addItemDecoration(divider);

        recyclerView.addItemDecoration(new ConversationLineItemDecoration(context));
        alwaysShowSensitiveMedia = accountManager.getActiveAccount().getAlwaysShowSensitiveMedia();
        alwaysOpenSpoiler = accountManager.getActiveAccount().getAlwaysOpenSpoiler();
        reloadFilters();

        recyclerView.setAdapter(adapter);

        statuses.clear();

        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        return rootView;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        onRefresh();

        eventHub.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(event -> {
                    if (event instanceof FavoriteEvent) {
                        handleFavEvent((FavoriteEvent) event);
                    } else if (event instanceof ReblogEvent) {
                        handleReblogEvent((ReblogEvent) event);
                    } else if (event instanceof BookmarkEvent) {
                        handleBookmarkEvent((BookmarkEvent) event);
                    } else if (event instanceof PinEvent) {
                        handlePinEvent(((PinEvent) event));
                    } else if (event instanceof BlockEvent) {
                        removeAllByAccountId(((BlockEvent) event).getAccountId());
                    } else if (event instanceof StatusComposedEvent) {
                        handleStatusComposedEvent((StatusComposedEvent) event);
                    } else if (event instanceof StatusDeletedEvent) {
                        handleStatusDeletedEvent((StatusDeletedEvent) event);
                    }
                });
    }

    public void onRevealPressed() {
        boolean allExpanded = allExpanded();
        for (int i = 0; i < statuses.size(); i++) {
            updateViewData(i, statuses.getPairedItem(i).copyWithExpanded(!allExpanded));
        }
        updateRevealIcon();
    }

    private boolean allExpanded() {
        boolean allExpanded = true;
        for (int i = 0; i < statuses.size(); i++) {
            if (!statuses.getPairedItem(i).isExpanded()) {
                allExpanded = false;
                break;
            }
        }
        return allExpanded;
    }

    @Override
    public void onRefresh() {
        sendStatusRequest(thisThreadsStatusId);
        sendThreadRequest(thisThreadsStatusId);
    }

    @Override
    public void onReply(int position) {
        super.reply(statuses.get(position));
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Status status = statuses.get(position);

        timelineCases.reblog(statuses.get(position).getId(), reblog)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        this::replaceStatus,
                        (t) -> Log.d(TAG,
                                "Failed to reblog status: " + status.getId(), t)
                );
    }

    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Status status = statuses.get(position);

        timelineCases.favourite(statuses.get(position).getId(), favourite)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        this::replaceStatus,
                        (t) -> Log.d(TAG,
                                "Failed to favourite status: " + status.getId(), t)
                );
    }

    @Override
    public void onBookmark(final boolean bookmark, final int position) {
        final Status status = statuses.get(position);

        timelineCases.bookmark(statuses.get(position).getId(), bookmark)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        this::replaceStatus,
                        (t) -> Log.d(TAG,
                                "Failed to bookmark status: " + status.getId(), t)
                );
    }

    private void replaceStatus(Status status) {
        updateStatus(status.getId(), (__) -> status);
    }

    private void updateStatus(String statusId, Function<Status, Status> mapper) {
        int position = indexOfStatus(statusId);

        if (position >= 0 && position < statuses.size()) {
            Status oldStatus = statuses.get(position);
            Status newStatus = mapper.apply(oldStatus);
            StatusViewData.Concrete oldViewData = statuses.getPairedItem(position);
            statuses.set(position, newStatus);
            updateViewData(position, oldViewData.copyWithStatus(newStatus));
        }
    }

    @Override
    public void onMore(@NonNull View view, int position) {
        super.more(statuses.get(position), view, position);
    }

    @Override
    public void onViewMedia(int position, int attachmentIndex, @NonNull View view) {
        Status status = statuses.get(position);
        super.viewMedia(attachmentIndex, AttachmentViewData.list(status), view);
    }

    @Override
    public void onViewThread(int position) {
        Status status = statuses.get(position);
        if (thisThreadsStatusId.equals(status.getId())) {
            // If already viewing this thread, don't reopen it.
            return;
        }
        super.viewThread(status.getActionableId(), status.getActionableStatus().getUrl());
    }

    @Override
    public void onOpenReblog(int position) {
        // there should be no reblogs in the thread but let's implement it to be sure
        super.openReblog(statuses.get(position));
    }

    @Override
    public void onExpandedChange(boolean expanded, int position) {
        updateViewData(
                position,
                statuses.getPairedItem(position).copyWithExpanded(expanded)
        );
        updateRevealIcon();
    }

    @Override
    public void onContentHiddenChange(boolean isShowing, int position) {
        updateViewData(
                position,
                statuses.getPairedItem(position).copyWithShowingContent(isShowing)
        );
    }

    private void updateViewData(int position, StatusViewData.Concrete newViewData) {
        statuses.setPairedItem(position, newViewData);
        adapter.setItem(position, newViewData, true);
    }

    @Override
    public void onLoadMore(int position) {

    }

    @Override
    public void onShowReblogs(int position) {
        String statusId = statuses.get(position).getId();
        Intent intent = AccountListActivity.newIntent(getContext(), AccountListActivity.Type.REBLOGGED, statusId);
        ((BaseActivity) getActivity()).startActivityWithSlideInAnimation(intent);
    }

    @Override
    public void onShowFavs(int position) {
        String statusId = statuses.get(position).getId();
        Intent intent = AccountListActivity.newIntent(getContext(), AccountListActivity.Type.FAVOURITED, statusId);
        ((BaseActivity) getActivity()).startActivityWithSlideInAnimation(intent);
    }

    @Override
    public void onContentCollapsedChange(boolean isCollapsed, int position) {
        adapter.setItem(
                position,
                statuses.getPairedItem(position).copyWIthCollapsed(isCollapsed),
                true
        );
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

    public void onVoteInPoll(int position, @NonNull List<Integer> choices) {
        final Status status = statuses.get(position).getActionableStatus();

        setVoteForPoll(status.getId(), status.getPoll().votedCopy(choices));

        timelineCases.voteInPoll(status.getId(), status.getPoll().getId(), choices)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        (newPoll) -> setVoteForPoll(status.getId(), newPoll),
                        (t) -> Log.d(TAG,
                                "Failed to vote in poll: " + status.getId(), t)
                );

    }

    private void setVoteForPoll(String statusId, Poll newPoll) {
        updateStatus(statusId, s -> s.copyWithPoll(newPoll));
    }

    private void removeAllByAccountId(String accountId) {
        Status status = null;
        if (!statuses.isEmpty()) {
            status = statuses.get(statusIndex);
        }
        // using iterator to safely remove items while iterating
        Iterator<Status> iterator = statuses.iterator();
        while (iterator.hasNext()) {
            Status s = iterator.next();
            if (s.getAccount().getId().equals(accountId) || s.getActionableStatus().getAccount().getId().equals(accountId)) {
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
        mastodonApi.status(id)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        status -> {
                            int position = setStatus(status);
                            recyclerView.scrollToPosition(position);
                        },
                        throwable -> onThreadRequestFailure(id, throwable)
                );
    }

    private void sendThreadRequest(final String id) {
        mastodonApi.statusContext(id)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        context -> {
                            swipeRefreshLayout.setRefreshing(false);
                            setContext(context.getAncestors(), context.getDescendants());
                        },
                        throwable -> onThreadRequestFailure(id, throwable)
                );
    }

    private void onThreadRequestFailure(final String id, final Throwable throwable) {
        View view = getView();
        swipeRefreshLayout.setRefreshing(false);
        if (view != null) {
            Snackbar.make(view, R.string.error_generic, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_retry, v -> {
                        sendThreadRequest(id);
                        sendStatusRequest(id);
                    })
                    .show();
        } else {
            Log.e(TAG, "Network request failed", throwable);
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
        adapter.addItem(i, statuses.getPairedItem(i));
        updateRevealIcon();
        return i;
    }

    private void setContext(List<Status> unfilteredAncestors, List<Status> unfilteredDescendants) {
        Status mainStatus = null;

        // In case of refresh, remove old ancestors and descendants first. We'll remove all blindly,
        // as we have no guarantee on their order to be the same as before
        int oldSize = statuses.size();
        if (oldSize > 1) {
            mainStatus = statuses.get(statusIndex);
            statuses.clear();
            adapter.clearItems();
        }

        ArrayList<Status> ancestors = new ArrayList<>();
        for (Status status : unfilteredAncestors)
            if (!filterModel.shouldFilterStatus(status))
                ancestors.add(status);

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
            // Not filtering the main status, since the user explicitly chose to be here
            statuses.add(statusIndex, mainStatus);
            StatusViewData.Concrete viewData = statuses.getPairedItem(statusIndex);

            adapter.addItem(statusIndex, viewData);
        }

        ArrayList<Status> descendants = new ArrayList<>();
        for (Status status : unfilteredDescendants)
            if (!filterModel.shouldFilterStatus(status))
                descendants.add(status);

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
        updateRevealIcon();
    }

    private void handleFavEvent(FavoriteEvent event) {
        updateStatus(event.getStatusId(), (s) -> {
            s.setFavourited(event.getFavourite());
            return s;
        });
    }

    private void handleReblogEvent(ReblogEvent event) {
        updateStatus(event.getStatusId(), (s) -> {
            s.setReblogged(event.getReblog());
            return s;
        });
    }

    private void handleBookmarkEvent(BookmarkEvent event) {
        updateStatus(event.getStatusId(), (s) -> {
            s.setBookmarked(event.getBookmark());
            return s;
        });
    }

    private void handlePinEvent(PinEvent event) {
        updateStatus(event.getStatusId(), (s) -> s.copyWithPinned(event.getPinned()));
    }


    private void handleStatusComposedEvent(StatusComposedEvent event) {
        Status eventStatus = event.getStatus();
        if (eventStatus.getInReplyToId() == null) return;

        if (eventStatus.getInReplyToId().equals(thisThreadsStatusId)) {
            insertStatus(eventStatus, statuses.size());
        } else {
            // If new status is a reply to some status in the thread, insert new status after it
            // We only check statuses below main status, ones on top don't belong to this thread
            for (int i = statusIndex; i < statuses.size(); i++) {
                Status status = statuses.get(i);
                if (eventStatus.getInReplyToId().equals(status.getId())) {
                    insertStatus(eventStatus, i + 1);
                    break;
                }
            }
        }
    }

    private void insertStatus(Status status, int at) {
        statuses.add(at, status);
        adapter.addItem(at, statuses.getPairedItem(at));
    }

    private void handleStatusDeletedEvent(StatusDeletedEvent event) {
        int index = this.indexOfStatus(event.getStatusId());
        if (index != -1) {
            statuses.remove(index);
            adapter.removeItem(index);
        }
    }


    private int indexOfStatus(String statusId) {
        return CollectionsKt.indexOfFirst(this.statuses, (s) -> s.getId().equals(statusId));
    }

    private void updateRevealIcon() {
        ViewThreadActivity activity = ((ViewThreadActivity) getActivity());
        if (activity == null) return;

        boolean hasAnyWarnings = false;
        // Statuses are updated from the main thread so nothing should change while iterating
        for (int i = 0; i < statuses.size(); i++) {
            if (!TextUtils.isEmpty(statuses.get(i).getSpoilerText())) {
                hasAnyWarnings = true;
                break;
            }
        }
        if (!hasAnyWarnings) {
            activity.setRevealButtonState(ViewThreadActivity.REVEAL_BUTTON_HIDDEN);
            return;
        }
        activity.setRevealButtonState(allExpanded() ? ViewThreadActivity.REVEAL_BUTTON_HIDE :
                ViewThreadActivity.REVEAL_BUTTON_REVEAL);
    }

    private void reloadFilters() {
        mastodonApi.getFilters()
                .to(autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(
                        (filters) -> {
                            List<Filter> relevantFilters = CollectionsKt.filter(
                                    filters,
                                    (f) -> f.getContext().contains(Filter.THREAD)
                            );
                            filterModel.initWithFilters(relevantFilters);

                            recyclerView.post(this::applyFilters);
                        },
                        (t) -> Log.e(TAG, "Failed to load filters", t)
                );
    }

    private void applyFilters() {
        CollectionsKt.removeAll(this.statuses, filterModel::shouldFilterStatus);
        adapter.setStatuses(this.statuses.getPairedCopy());
    }
}
