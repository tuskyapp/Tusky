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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.core.util.Pair;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.keylesspalace.tusky.AccountListActivity;
import com.keylesspalace.tusky.BaseActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder;
import com.keylesspalace.tusky.adapter.TimelineAdapter;
import com.keylesspalace.tusky.appstore.BlockEvent;
import com.keylesspalace.tusky.appstore.DomainMuteEvent;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.FavoriteEvent;
import com.keylesspalace.tusky.appstore.MuteEvent;
import com.keylesspalace.tusky.appstore.NewHomeTimelineStatusEvent;
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent;
import com.keylesspalace.tusky.appstore.ProflileStreamingReconnectedEvent;
import com.keylesspalace.tusky.appstore.ReblogEvent;
import com.keylesspalace.tusky.appstore.StatusComposedEvent;
import com.keylesspalace.tusky.appstore.StatusDeletedEvent;
import com.keylesspalace.tusky.appstore.UnfollowEvent;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Filter;
import com.keylesspalace.tusky.entity.Poll;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.interfaces.RefreshableFragment;
import com.keylesspalace.tusky.interfaces.ReselectableFragment;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.repository.Placeholder;
import com.keylesspalace.tusky.repository.TimelineRepository;
import com.keylesspalace.tusky.repository.TimelineRequestMode;
import com.keylesspalace.tusky.util.Either;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.StringUtils;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.BackgroundMessageView;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import at.connyduck.sparkbutton.helpers.Utils;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;

public class TimelineFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener,
        StatusActionListener,
        Injectable, ReselectableFragment, RefreshableFragment {
    private static final String TAG = "TimelineF"; // logging tag
    private static final String KIND_ARG = "kind";
    private static final String HASHTAG_OR_ID_ARG = "hashtag_or_id";
    private static final String ARG_ENABLE_SWIPE_TO_REFRESH = "arg.enable.swipe.to.refresh";

    private static final int LOAD_AT_ONCE = 30;
    private boolean isSwipeToRefreshEnabled = true;
    private boolean isNeedRefresh;
    private List<String> newStreamingStatuses = new ArrayList<>();

    public enum Kind {
        HOME,
        PUBLIC_LOCAL,
        PUBLIC_FEDERATED,
        TAG,
        USER,
        USER_PINNED,
        USER_WITH_REPLIES,
        FAVOURITES,
        LIST
    }

    private enum FetchEnd {
        TOP,
        BOTTOM,
        MIDDLE
    }

    @Inject
    public EventHub eventHub;
    @Inject
    TimelineRepository timelineRepo;

    @Inject
    public AccountManager accountManager;

    private boolean eventRegistered = false;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ContentLoadingProgressBar topProgressBar;
    private BackgroundMessageView statusView;

    private TimelineAdapter adapter;
    private Kind kind;
    private String hashtagOrId;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private boolean filterRemoveReplies;
    private boolean filterRemoveReblogs;
    private boolean hideFab;
    private boolean bottomLoading;

    private boolean didLoadEverythingBottom;
    private boolean alwaysShowSensitiveMedia;
    private boolean alwaysOpenSpoiler;
    private boolean initialUpdateFailed = false;
    private Single<?> loadingAbove;

    private PairedList<Either<Placeholder, Status>, StatusViewData> statuses =
            new PairedList<>(new Function<Either<Placeholder, Status>, StatusViewData>() {
                @Override
                public StatusViewData apply(Either<Placeholder, Status> input) {
                    Status status = input.asRightOrNull();
                    if (status != null) {
                        return ViewDataUtils.statusToViewData(
                                status,
                                alwaysShowSensitiveMedia,
                                alwaysOpenSpoiler
                        );
                    } else {
                        Placeholder placeholder = input.asLeft();
                        return new StatusViewData.Placeholder(placeholder.getId(), false);
                    }
                }
            });

    public static TimelineFragment newInstance(Kind kind) {
        return newInstance(kind, null);
    }

    public static TimelineFragment newInstance(Kind kind, @Nullable String hashtagOrId) {
        return newInstance(kind, hashtagOrId, true);
    }

    public static TimelineFragment newInstance(Kind kind, @Nullable String hashtagOrId, boolean enableSwipeToRefresh) {
        TimelineFragment fragment = new TimelineFragment();
        Bundle arguments = new Bundle();
        arguments.putString(KIND_ARG, kind.name());
        arguments.putString(HASHTAG_OR_ID_ARG, hashtagOrId);
        arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, enableSwipeToRefresh);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = Objects.requireNonNull(getArguments());
        kind = Kind.valueOf(arguments.getString(KIND_ARG));
        if (kind == Kind.TAG
                || kind == Kind.USER
                || kind == Kind.USER_PINNED
                || kind == Kind.USER_WITH_REPLIES
                || kind == Kind.LIST) {
            hashtagOrId = arguments.getString(HASHTAG_OR_ID_ARG);
        }

        adapter = new TimelineAdapter(dataSource, this);

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        recyclerView = rootView.findViewById(R.id.recyclerView);
        swipeRefreshLayout = rootView.findViewById(R.id.swipeRefreshLayout);
        progressBar = rootView.findViewById(R.id.progressBar);
        statusView = rootView.findViewById(R.id.statusView);
        topProgressBar = rootView.findViewById(R.id.topProgressBar);

        setupSwipeRefreshLayout();
        setupRecyclerView();
        updateAdapter();
        setupTimelinePreferences();

        if (statuses.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
            bottomLoading = true;
            this.sendInitialRequest();
        } else {
            progressBar.setVisibility(View.GONE);
            if (isNeedRefresh)
                onRefresh();
        }

        return rootView;
    }

    private void sendInitialRequest() {
        if (this.kind == Kind.HOME) {
            this.tryCache();
        } else {
            sendFetchTimelineRequest(null, null, null, FetchEnd.BOTTOM, -1);
        }
    }

    private void tryCache() {
        // Request timeline from disk to make it quick, then replace it with timeline from
        // the server to update it
        this.timelineRepo.getStatuses(null, null, null, LOAD_AT_ONCE,
                TimelineRequestMode.DISK)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(statuses -> {
                    filterStatuses(statuses);

                    if (statuses.size() > 1) {
                        this.clearPlaceholdersForResponse(statuses);
                        this.statuses.clear();
                        this.statuses.addAll(statuses);
                        this.updateAdapter();
                        this.progressBar.setVisibility(View.GONE);
                        // Request statuses including current top to refresh all of them
                    }

                    this.updateCurrent();
                    this.loadAbove();
                });
    }

    private void updateCurrent() {
        String topId;
        if (this.statuses.isEmpty()) {
            topId = null;
        } else {
            topId = CollectionsKt.first(this.statuses, Either::isRight).asRight().getId();
        }
        this.timelineRepo.getStatuses(topId, null, null, LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        (statuses) -> {
                            this.initialUpdateFailed = false;
                            // When cached timeline is too old, we would replace it with nothing
                            if (!statuses.isEmpty()) {
                                filterStatuses(statuses);

                                if (!this.statuses.isEmpty() && topId != null) {
                                    // clear old cached statuses
                                    Iterator<Either<Placeholder, Status>> iterator = this.statuses.iterator();
                                    while (iterator.hasNext()) {
                                        Either<Placeholder, Status> item = iterator.next();
                                        if (item.isRight()) {
                                            Status status = item.asRight();
                                            if (status.getId().length() < topId.length() || status.getId().compareTo(topId) < 0) {

                                                iterator.remove();
                                            }
                                        } else {
                                            Placeholder placeholder = item.asLeft();
                                            if (placeholder.getId().length() < topId.length() || placeholder.getId().compareTo(topId) < 0) {

                                                iterator.remove();
                                            }
                                        }

                                    }
                                }

                                this.statuses.addAll(statuses);
                                this.updateAdapter();
                            }
                            this.bottomLoading = false;

                        },
                        (e) -> {
                            this.initialUpdateFailed = true;
                            // Indicate that we are not loading anymore
                            this.progressBar.setVisibility(View.GONE);
                            this.swipeRefreshLayout.setRefreshing(false);
                        });
    }

    private void setupTimelinePreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        alwaysShowSensitiveMedia = accountManager.getActiveAccount().getAlwaysShowSensitiveMedia();
        alwaysOpenSpoiler = accountManager.getActiveAccount().getAlwaysOpenSpoiler();
        boolean mediaPreviewEnabled = accountManager.getActiveAccount().getMediaPreviewEnabled();
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        boolean useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false);
        adapter.setUseAbsoluteTime(useAbsoluteTime);
        boolean showBotOverlay = preferences.getBoolean("showBotOverlay", true);
        adapter.setShowBotOverlay(showBotOverlay);
        boolean animateAvatar = preferences.getBoolean("animateGifAvatars", false);
        adapter.setAnimateAvatar(animateAvatar);

        boolean filter = preferences.getBoolean("tabFilterHomeReplies", true);
        filterRemoveReplies = kind == Kind.HOME && !filter;

        filter = preferences.getBoolean("tabFilterHomeBoosts", true);
        filterRemoveReblogs = kind == Kind.HOME && !filter;
        reloadFilters(false);
    }

    private static boolean filterContextMatchesKind(Kind kind, List<String> filterContext) {
        // home, notifications, public, thread
        switch (kind) {
            case HOME:
                return filterContext.contains(Filter.HOME);
            case PUBLIC_FEDERATED:
            case PUBLIC_LOCAL:
            case TAG:
                return filterContext.contains(Filter.PUBLIC);
            case FAVOURITES:
                return (filterContext.contains(Filter.PUBLIC) || filterContext.contains(Filter.NOTIFICATIONS));
            default:
                return false;
        }
    }

    @Override
    protected boolean filterIsRelevant(Filter filter) {
        return filterContextMatchesKind(kind, filter.getContext());
    }

    @Override
    protected void refreshAfterApplyingFilters() {
        fullyRefresh();
    }

    private void setupSwipeRefreshLayout() {
        swipeRefreshLayout.setEnabled(isSwipeToRefreshEnabled);
        if (isSwipeToRefreshEnabled) {
            Context context = swipeRefreshLayout.getContext();
            swipeRefreshLayout.setOnRefreshListener(this);
            swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue);
            swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(context,
                    android.R.attr.colorBackground));
        }
    }

    private void setupRecyclerView() {
        recyclerView.setAccessibilityDelegateCompat(
                new ListStatusAccessibilityDelegate(recyclerView, this, statuses::getPairedItemOrNull));
        Context context = recyclerView.getContext();
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        recyclerView.addItemDecoration(divider);

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        recyclerView.setAdapter(adapter);
    }

    private void deleteStatusById(String id) {
        for (int i = 0; i < statuses.size(); i++) {
            Either<Placeholder, Status> either = statuses.get(i);
            if (either.isRight()
                    && id.equals(either.asRight().getId())) {
                statuses.remove(either);
                updateAdapter();
                break;
            }
        }
        if (statuses.size() == 0) {
            showNothing();
        }
    }

    private void showNothing() {
        statusView.setVisibility(View.VISIBLE);
        statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

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
                public void onLoadMore(int totalItemsCount, RecyclerView view) {
                    TimelineFragment.this.onLoadMore();
                }
            };
        } else {
            // Just use the basic scroll listener to load more statuses.
            scrollListener = new EndlessOnScrollListener(layoutManager) {
                @Override
                public void onLoadMore(int totalItemsCount, RecyclerView view) {
                    TimelineFragment.this.onLoadMore();
                }
            };
        }
        recyclerView.addOnScrollListener(scrollListener);

        if (!eventRegistered) {
            eventHub.getEvents()
                    .observeOn(AndroidSchedulers.mainThread())
                    .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                    .subscribe(event -> {
                        if (event instanceof FavoriteEvent) {
                            FavoriteEvent favEvent = ((FavoriteEvent) event);
                            handleFavEvent(favEvent);
                        } else if (event instanceof ReblogEvent) {
                            ReblogEvent reblogEvent = (ReblogEvent) event;
                            handleReblogEvent(reblogEvent);
                        } else if (event instanceof UnfollowEvent) {
                            if (kind == Kind.HOME) {
                                String id = ((UnfollowEvent) event).getAccountId();
                                removeAllByAccountId(id);
                            }
                        } else if (event instanceof BlockEvent) {
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                String id = ((BlockEvent) event).getAccountId();
                                removeAllByAccountId(id);
                            }
                        } else if (event instanceof MuteEvent) {
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                String id = ((MuteEvent) event).getAccountId();
                                removeAllByAccountId(id);
                            }
                        } else if (event instanceof DomainMuteEvent) {
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                String instance = ((DomainMuteEvent) event).getInstance();
                                removeAllByInstance(instance);
                            }
                        } else if (event instanceof StatusDeletedEvent) {
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES && kind != Kind.USER_PINNED) {
                                String id = ((StatusDeletedEvent) event).getStatusId();
                                deleteStatusById(id);
                            }
                        } else if (event instanceof StatusComposedEvent) {
                            Status status = ((StatusComposedEvent) event).getStatus();
                            handleStatusComposeEvent(status);
                        } else if (event instanceof PreferenceChangedEvent) {
                            onPreferenceChanged(((PreferenceChangedEvent) event).getPreferenceKey());
                        } else if (event instanceof NewHomeTimelineStatusEvent && kind == Kind.HOME) {
                            this.loadingAbove.as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                                    .subscribe((__) -> {
                                        Status status =
                                                ((NewHomeTimelineStatusEvent) event).getStatus();
                                        Either.Right<Placeholder, Status> either =
                                                new Either.Right<>(status);
                                        if (!this.statuses.contains(either)) {
                                            this.newStreamingStatuses.add(status.getId());
                                            this.statuses.add(0, either);
                                            this.updateAdapter();
                                        }
                                    });
                        } else if (event instanceof ProflileStreamingReconnectedEvent) {
                            this.loadAbove();
                        }
                    });
            eventRegistered = true;
        }
    }

    @Override
    public void onRefresh() {
        if (isSwipeToRefreshEnabled)
            swipeRefreshLayout.setEnabled(true);
        this.statusView.setVisibility(View.GONE);
        isNeedRefresh = false;
        if (this.initialUpdateFailed) {
            updateCurrent();
        }

        this.loadAbove();

    }

    private void loadAbove() {
        String firstOrNull = null;
        String secondOrNull = null;
        for (int i = 0; i < this.statuses.size(); i++) {
            Either<Placeholder, Status> status = this.statuses.get(i);
            if (status.isRight()) {
                firstOrNull = status.asRight().getId();
                if (i + 1 < statuses.size() && statuses.get(i + 1).isRight()) {
                    secondOrNull = statuses.get(i + 1).asRight().getId();
                }
                break;
            }
        }
        if (firstOrNull != null) {
            this.loadingAbove = this.sendFetchTimelineRequest(null, firstOrNull, secondOrNull, FetchEnd.TOP, -1);
        } else {
            this.loadingAbove = this.sendFetchTimelineRequest(null, null, null, FetchEnd.BOTTOM, -1);
        }
    }

    @Override
    public void onReply(int position) {
        super.reply(statuses.get(position).asRight());
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Status status = statuses.get(position).asRight();
        timelineCases.reblog(status, reblog)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        (newStatus) -> setRebloggedForStatus(position, status, reblog),
                        (err) -> Log.d(TAG, "Failed to reblog status " + status.getId(), err)
                );
    }

    private void setRebloggedForStatus(int position, Status status, boolean reblog) {
        status.setReblogged(reblog);

        if (status.getReblog() != null) {
            status.getReblog().setReblogged(reblog);
        }

        Pair<StatusViewData.Concrete, Integer> actual =
                findStatusAndPosition(position, status);
        if (actual == null) return;

        StatusViewData newViewData =
                new StatusViewData.Builder(actual.first)
                        .setReblogged(reblog)
                        .createStatusViewData();
        statuses.setPairedItem(actual.second, newViewData);
        updateAdapter();
    }

    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Status status = statuses.get(position).asRight();

        timelineCases.favourite(status, favourite)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        (newStatus) -> setFavouriteForStatus(position, newStatus, favourite),
                        (err) -> Log.d(TAG, "Failed to favourite status " + status.getId(), err)
                );
    }

    private void setFavouriteForStatus(int position, Status status, boolean favourite) {
        status.setFavourited(favourite);

        if (status.getReblog() != null) {
            status.getReblog().setFavourited(favourite);
        }

        Pair<StatusViewData.Concrete, Integer> actual =
                findStatusAndPosition(position, status);
        if (actual == null) return;

        StatusViewData newViewData = new StatusViewData
                .Builder(actual.first)
                .setFavourited(favourite)
                .createStatusViewData();
        statuses.setPairedItem(actual.second, newViewData);
        updateAdapter();
    }

    public void onVoteInPoll(int position, @NonNull List<Integer> choices) {

        final Status status = statuses.get(position).asRight();

        Poll votedPoll = status.getActionableStatus().getPoll().votedCopy(choices);

        setVoteForPoll(position, status, votedPoll);

        timelineCases.voteInPoll(status, choices)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this)))
                .subscribe(
                        (newPoll) -> setVoteForPoll(position, status, newPoll),
                        (t) -> Log.d(TAG,
                                "Failed to vote in poll: " + status.getId(), t)
                );
    }

    private void setVoteForPoll(int position, Status status, Poll newPoll) {
        Pair<StatusViewData.Concrete, Integer> actual =
                findStatusAndPosition(position, status);
        if (actual == null) return;

        StatusViewData newViewData = new StatusViewData
                .Builder(actual.first)
                .setPoll(newPoll)
                .createStatusViewData();
        statuses.setPairedItem(actual.second, newViewData);
        updateAdapter();
    }

    @Override
    public void onMore(@NonNull View view, final int position) {
        super.more(statuses.get(position).asRight(), view, position);
    }

    @Override
    public void onOpenReblog(int position) {
        super.openReblog(statuses.get(position).asRight());
    }

    @Override
    public void onExpandedChange(boolean expanded, int position) {
        StatusViewData newViewData = new StatusViewData.Builder(
                ((StatusViewData.Concrete) statuses.getPairedItem(position)))
                .setIsExpanded(expanded).createStatusViewData();
        statuses.setPairedItem(position, newViewData);
        updateAdapter();
    }

    @Override
    public void onContentHiddenChange(boolean isShowing, int position) {
        StatusViewData newViewData = new StatusViewData.Builder(
                ((StatusViewData.Concrete) statuses.getPairedItem(position)))
                .setIsShowingSensitiveContent(isShowing).createStatusViewData();
        statuses.setPairedItem(position, newViewData);
        updateAdapter();
    }


    @Override
    public void onShowReblogs(int position) {
        String statusId = statuses.get(position).asRight().getId();
        Intent intent = AccountListActivity.newIntent(getContext(), AccountListActivity.Type.REBLOGGED, statusId);
        ((BaseActivity) getActivity()).startActivityWithSlideInAnimation(intent);
    }

    @Override
    public void onShowFavs(int position) {
        String statusId = statuses.get(position).asRight().getId();
        Intent intent = AccountListActivity.newIntent(getContext(), AccountListActivity.Type.FAVOURITED, statusId);
        ((BaseActivity) getActivity()).startActivityWithSlideInAnimation(intent);
    }

    @Override
    public void onLoadMore(int position) {
        //check bounds before accessing list,
        if (statuses.size() >= position && position > 0) {
            Status fromStatus = statuses.get(position - 1).asRightOrNull();
            Status toStatus = statuses.get(position + 1).asRightOrNull();
            String maxMinusOne =
                    statuses.size() > position + 1 && statuses.get(position + 2).isRight()
                            ? statuses.get(position + 1).asRight().getId()
                            : null;
            if (fromStatus == null || toStatus == null) {
                Log.e(TAG, "Failed to load more at " + position + ", wrong placeholder position");
                return;
            }
            sendFetchTimelineRequest(fromStatus.getId(), toStatus.getId(), maxMinusOne,
                    FetchEnd.MIDDLE, position);

            Placeholder placeholder = statuses.get(position).asLeft();
            StatusViewData newViewData = new StatusViewData.Placeholder(placeholder.getId(), true);
            statuses.setPairedItem(position, newViewData);
            updateAdapter();
        } else {
            Log.e(TAG, "error loading more");
        }
    }

    @Override
    public void onContentCollapsedChange(boolean isCollapsed, int position) {
        if (position < 0 || position >= statuses.size()) {
            Log.e(TAG, String.format("Tried to access out of bounds status position: %d of %d", position, statuses.size() - 1));
            return;
        }

        StatusViewData status = statuses.getPairedItem(position);
        if (!(status instanceof StatusViewData.Concrete)) {
            // Statuses PairedList contains a base type of StatusViewData.Concrete and also doesn't
            // check for null values when adding values to it although this doesn't seem to be an issue.
            Log.e(TAG, String.format(
                    "Expected StatusViewData.Concrete, got %s instead at position: %d of %d",
                    status == null ? "<null>" : status.getClass().getSimpleName(),
                    position,
                    statuses.size() - 1
            ));
            return;
        }

        StatusViewData updatedStatus = new StatusViewData.Builder((StatusViewData.Concrete) status)
                .setCollapsed(isCollapsed)
                .createStatusViewData();
        statuses.setPairedItem(position, updatedStatus);
        updateAdapter();
    }

    @Override
    public void onViewMedia(int position, int attachmentIndex, @Nullable View view) {
        Status status = statuses.get(position).asRightOrNull();
        if (status == null) return;
        super.viewMedia(attachmentIndex, status, view);
    }

    @Override
    public void onViewThread(int position) {
        super.viewThread(statuses.get(position).asRight());
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
        if ((kind == Kind.USER || kind == Kind.USER_WITH_REPLIES) && hashtagOrId.equals(id)) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return;
        }
        super.viewAccount(id);
    }

    private void onPreferenceChanged(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        switch (key) {
            case "fabHide": {
                hideFab = sharedPreferences.getBoolean("fabHide", false);
                break;
            }
            case "mediaPreviewEnabled": {
                boolean enabled = accountManager.getActiveAccount().getMediaPreviewEnabled();
                boolean oldMediaPreviewEnabled = adapter.getMediaPreviewEnabled();
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.setMediaPreviewEnabled(enabled);
                    fullyRefresh();
                }
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
            case Filter.HOME:
            case Filter.NOTIFICATIONS:
            case Filter.THREAD:
            case Filter.PUBLIC: {
                if (filterContextMatchesKind(kind, Collections.singletonList(key))) {
                    reloadFilters(true);
                }
                break;
            }
            case "alwaysShowSensitiveMedia": {
                //it is ok if only newly loaded statuses are affected, no need to fully refresh
                alwaysShowSensitiveMedia = accountManager.getActiveAccount().getAlwaysShowSensitiveMedia();
                break;
            }
        }
    }

    @Override
    public void removeItem(int position) {
        statuses.remove(position);
        updateAdapter();
    }

    private void removeAllByAccountId(String accountId) {
        // using iterator to safely remove items while iterating
        Iterator<Either<Placeholder, Status>> iterator = statuses.iterator();
        while (iterator.hasNext()) {
            Status status = iterator.next().asRightOrNull();
            if (status != null &&
                    (status.getAccount().getId().equals(accountId) || status.getActionableStatus().getAccount().getId().equals(accountId))) {
                iterator.remove();
            }
        }
        updateAdapter();
    }

    private void removeAllByInstance(String instance) {
        // using iterator to safely remove items while iterating
        Iterator<Either<Placeholder, Status>> iterator = statuses.iterator();
        while (iterator.hasNext()) {
            Status status = iterator.next().asRightOrNull();
            if (status != null && LinkHelper.getDomain(status.getAccount().getUrl()).equals(instance)) {
                iterator.remove();
            }
        }
        updateAdapter();
    }

    private void onLoadMore() {
        if (didLoadEverythingBottom || bottomLoading) {
            return;
        }

        if (statuses.size() == 0) {
            sendInitialRequest();
            return;
        }

        bottomLoading = true;

        Either<Placeholder, Status> last = statuses.get(statuses.size() - 1);
        Placeholder placeholder;
        if (last.isRight()) {
            final String placeholderId = StringUtils.dec(last.asRight().getId());
            placeholder = new Placeholder(placeholderId);
            statuses.add(new Either.Left<>(placeholder));
        } else {
            placeholder = last.asLeft();
        }
        statuses.setPairedItem(statuses.size() - 1,
                new StatusViewData.Placeholder(placeholder.getId(), true));

        updateAdapter();

        String bottomId = null;
        final ListIterator<Either<Placeholder, Status>> iterator =
                this.statuses.listIterator(this.statuses.size());
        while (iterator.hasPrevious()) {
            Either<Placeholder, Status> previous = iterator.previous();
            if (previous.isRight()) {
                bottomId = previous.asRight().getId();
                break;
            }
        }
        sendFetchTimelineRequest(bottomId, null, null, FetchEnd.BOTTOM, -1);
    }

    private void fullyRefresh() {
        statuses.clear();
        updateAdapter();
        bottomLoading = true;
        sendFetchTimelineRequest(null, null, null, FetchEnd.BOTTOM, -1);
    }

    private boolean actionButtonPresent() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES &&
                getActivity() instanceof ActionButtonActivity;
    }

    private void jumpToTop() {
        if (isAdded()) {
            layoutManager.scrollToPosition(0);
            recyclerView.stopScroll();
            scrollListener.reset();
        }
    }

    private Call<List<Status>> getFetchCallByTimelineType(Kind kind, String tagOrId, String fromId,
                                                          String uptoId) {
        MastodonApi api = mastodonApi;
        switch (kind) {
            default:
            case HOME:
                return api.homeTimeline(fromId, uptoId, LOAD_AT_ONCE);
            case PUBLIC_FEDERATED:
                return api.publicTimeline(null, fromId, uptoId, LOAD_AT_ONCE);
            case PUBLIC_LOCAL:
                return api.publicTimeline(true, fromId, uptoId, LOAD_AT_ONCE);
            case TAG:
                return api.hashtagTimeline(tagOrId, null, fromId, uptoId, LOAD_AT_ONCE);
            case USER:
                return api.accountStatuses(tagOrId, fromId, uptoId, LOAD_AT_ONCE, true, null, null);
            case USER_PINNED:
                return api.accountStatuses(tagOrId, fromId, uptoId, LOAD_AT_ONCE, null, null, true);
            case USER_WITH_REPLIES:
                return api.accountStatuses(tagOrId, fromId, uptoId, LOAD_AT_ONCE, null, null, null);
            case FAVOURITES:
                return api.favourites(fromId, uptoId, LOAD_AT_ONCE);
            case LIST:
                return api.listTimeline(tagOrId, fromId, uptoId, LOAD_AT_ONCE);
        }
    }

    private Single<?> sendFetchTimelineRequest(@Nullable String maxId, @Nullable String sinceId,
                                               @Nullable String sinceIdMinusOne,
                                               final FetchEnd fetchEnd, final int pos) {
        if (isAdded() && (fetchEnd == FetchEnd.TOP || fetchEnd == FetchEnd.BOTTOM && maxId == null && progressBar.getVisibility() != View.VISIBLE) && !isSwipeToRefreshEnabled)
            topProgressBar.show();

        if (kind == Kind.HOME) {
            TimelineRequestMode mode;
            // allow getting old statuses/fallbacks for network only for for bottom loading
            if (fetchEnd == FetchEnd.BOTTOM) {
                mode = TimelineRequestMode.ANY;
            } else {
                mode = TimelineRequestMode.NETWORK;
            }

            Single<?> result =
                    timelineRepo.getStatuses(maxId, sinceId, sinceIdMinusOne, LOAD_AT_ONCE, mode)
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnSuccess((response) -> onFetchTimelineSuccess(response, fetchEnd, pos))
                            .doOnError((err) -> onFetchTimelineFailure(new Exception(err), fetchEnd, pos))
                            .cache(); // So that multiple subscriptions don't call API multiple times

            result
                    .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                    .subscribe();
            return result;
        } else {
            Single<?> result = Single
                    .create((supplier) -> {
                        Callback<List<Status>> callback = new Callback<List<Status>>() {
                            @Override
                            public void onResponse(@NonNull Call<List<Status>> call, @NonNull Response<List<Status>> response) {
                                if (response.isSuccessful()) {
                                    onFetchTimelineSuccess(liftStatusList(response.body()), fetchEnd, pos);
                                    supplier.onSuccess(response.body());
                                } else {
                                    Exception exception = new Exception(response.message());
                                    onFetchTimelineFailure(exception, fetchEnd, pos);
                                    supplier.onError(exception);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<List<Status>> call, @NonNull Throwable t) {
                                onFetchTimelineFailure((Exception) t, fetchEnd, pos);
                                supplier.onError(t);
                            }
                        };

                        Call<List<Status>> listCall = getFetchCallByTimelineType(kind, hashtagOrId, maxId, sinceId);
                        callList.add(listCall);
                        listCall.enqueue(callback);
                    })
                    .cache();
            result
                    .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                    .subscribe();
            return result;
        }
    }

    private void onFetchTimelineSuccess(List<Either<Placeholder, Status>> statuses,
                                        FetchEnd fetchEnd, int pos) {

        // We filled the hole (or reached the end) if the server returned less statuses than we
        // we asked for.
        boolean fullFetch = statuses.size() >= LOAD_AT_ONCE;
        filterStatuses(statuses);
        switch (fetchEnd) {
            case TOP: {
                updateStatuses(statuses, fullFetch);
                break;
            }
            case MIDDLE: {
                replacePlaceholderWithStatuses(statuses, fullFetch, pos);
                break;
            }
            case BOTTOM: {
                if (!this.statuses.isEmpty()
                        && !this.statuses.get(this.statuses.size() - 1).isRight()) {
                    this.statuses.remove(this.statuses.size() - 1);
                    updateAdapter();
                }

                if (!statuses.isEmpty() && !statuses.get(statuses.size() - 1).isRight()) {
                    // Removing placeholder if it's the last one from the cache
                    statuses.remove(statuses.size() - 1);
                }
                int oldSize = this.statuses.size();
                if (this.statuses.size() > 1) {
                    addItems(statuses);
                } else {
                    updateStatuses(statuses, fullFetch);
                }
                if (this.statuses.size() == oldSize) {
                    // This may be a brittle check but seems like it works
                    // Can we check it using headers somehow? Do all server support them?
                    didLoadEverythingBottom = true;
                }
                break;
            }
        }
        if (isAdded()) {
            topProgressBar.hide();
            updateBottomLoadingState(fetchEnd);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setEnabled(true);
            if (this.statuses.size() == 0) {
                this.showNothing();
            } else {
                this.statusView.setVisibility(View.GONE);
            }
        }
    }

    private void onFetchTimelineFailure(Exception exception, FetchEnd fetchEnd, int position) {
        if (isAdded()) {
            swipeRefreshLayout.setRefreshing(false);
            topProgressBar.hide();

            if (fetchEnd == FetchEnd.MIDDLE && !statuses.get(position).isRight()) {
                Placeholder placeholder = statuses.get(position).asLeftOrNull();
                StatusViewData newViewData;
                if (placeholder == null) {
                    Status above = statuses.get(position - 1).asRight();
                    String newId = StringUtils.dec(above.getId());
                    placeholder = new Placeholder(newId);
                }
                newViewData = new StatusViewData.Placeholder(placeholder.getId(), false);
                statuses.setPairedItem(position, newViewData);
                updateAdapter();
            } else if (this.statuses.isEmpty()) {
                swipeRefreshLayout.setEnabled(false);
                this.statusView.setVisibility(View.VISIBLE);
                if (exception instanceof IOException) {
                    this.statusView.setup(R.drawable.elephant_offline, R.string.error_network, __ -> {
                        this.progressBar.setVisibility(View.VISIBLE);
                        this.onRefresh();
                        return Unit.INSTANCE;
                    });
                } else {
                    this.statusView.setup(R.drawable.elephant_error, R.string.error_generic, __ -> {
                        this.progressBar.setVisibility(View.VISIBLE);
                        this.onRefresh();
                        return Unit.INSTANCE;
                    });
                }
            }

            Log.e(TAG, "Fetch Failure: " + exception.getMessage());
            updateBottomLoadingState(fetchEnd);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateBottomLoadingState(FetchEnd fetchEnd) {
        switch (fetchEnd) {
            case BOTTOM: {
                bottomLoading = false;
                break;
            }
        }
    }

    private void filterStatuses(List<Either<Placeholder, Status>> statuses) {
        Iterator<Either<Placeholder, Status>> it = statuses.iterator();
        while (it.hasNext()) {
            Status status = it.next().asRightOrNull();
            if (status != null
                    && ((status.getInReplyToId() != null && filterRemoveReplies)
                    || (status.getReblog() != null && filterRemoveReblogs)
                    || shouldFilterStatus(status))) {
                it.remove();
            }
        }
    }

    private void updateStatuses(List<Either<Placeholder, Status>> newStatuses, boolean fullFetch) {
        if (ListUtils.isEmpty(newStatuses)) {
            updateAdapter();
            return;
        }

        if (statuses.isEmpty()) {
            statuses.addAll(newStatuses);
        } else {
            Either<Placeholder, Status> lastOfNew = newStatuses.get(newStatuses.size() - 1);
            int index = statuses.indexOf(lastOfNew);

            if (index >= 0) {
                statuses.subList(0, index).clear();
            }

            int newIndex = newStatuses.indexOf(statuses.get(0));
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    String placeholderId = StringUtils.inc(
                            CollectionsKt.last(newStatuses, Either::isRight).asRight().getId());
                    newStatuses.add(new Either.Left<>(new Placeholder(placeholderId)));
                }
                statuses.addAll(0, newStatuses);
            } else {
                statuses.addAll(0, newStatuses.subList(0, newIndex));
            }
        }
        // Remove all consecutive placeholders
        removeConsecutivePlaceholders();
        updateAdapter();
    }

    private void removeConsecutivePlaceholders() {
        for (int i = 0; i < statuses.size() - 1; i++) {
            if (statuses.get(i).isLeft() && statuses.get(i + 1).isLeft()) {
                statuses.remove(i);
            }
        }
    }

    private void addItems(List<Either<Placeholder, Status>> newStatuses) {
        if (ListUtils.isEmpty(newStatuses)) {
            return;
        }
        Either<Placeholder, Status> last = null;
        for (int i = statuses.size() - 1; i >= 0; i--) {
            if (statuses.get(i).isRight()) {
                last = statuses.get(i);
                break;
            }
        }
        // I was about to replace findStatus with indexOf but it is incorrect to compare value
        // types by ID anyway and we should change equals() for Status, I think, so this makes sense
        if (last != null && !newStatuses.contains(last)) {
            statuses.addAll(newStatuses);
            removeConsecutivePlaceholders();
            updateAdapter();
        }
    }

    /**
     * For certain requests we don't want to see placeholders, they will be removed some other way
     */
    private void clearPlaceholdersForResponse(List<Either<Placeholder, Status>> statuses) {
        CollectionsKt.removeAll(statuses, Either::isLeft);
    }

    private void replacePlaceholderWithStatuses(List<Either<Placeholder, Status>> newStatuses,
                                                boolean fullFetch, int pos) {
        Either<Placeholder, Status> placeholder = statuses.get(pos);
        if (placeholder.isLeft()) {
            statuses.remove(pos);
        }

        if (ListUtils.isEmpty(newStatuses)) {
            updateAdapter();
            return;
        }

        if (fullFetch) {
            newStatuses.add(placeholder);
        }

        statuses.addAll(pos, newStatuses);
        removeConsecutivePlaceholders();

        updateAdapter();

    }

    private int findStatusOrReblogPositionById(@NonNull String statusId) {
        for (int i = 0; i < statuses.size(); i++) {
            Status status = statuses.get(i).asRightOrNull();
            if (status != null
                    && (statusId.equals(status.getId())
                    || (status.getReblog() != null
                    && statusId.equals(status.getReblog().getId())))) {
                return i;
            }
        }
        return -1;
    }

    private final Function1<Status, Either<Placeholder, Status>> statusLifter =
            Either.Right::new;

    private @Nullable
    Pair<StatusViewData.Concrete, Integer>
    findStatusAndPosition(int position, Status status) {
        StatusViewData.Concrete statusToUpdate;
        int positionToUpdate;
        StatusViewData someOldViewData = statuses.getPairedItem(position);

        // Unlikely, but data could change between the request and response
        if ((someOldViewData instanceof StatusViewData.Placeholder) ||
                !((StatusViewData.Concrete) someOldViewData).getId().equals(status.getId())) {
            // try to find the status we need to update
            int foundPos = statuses.indexOf(new Either.Right<Placeholder, Status>(status));
            if (foundPos < 0) return null; // okay, it's hopeless, give up
            statusToUpdate = ((StatusViewData.Concrete)
                    statuses.getPairedItem(foundPos));
            positionToUpdate = position;
        } else {
            statusToUpdate = (StatusViewData.Concrete) someOldViewData;
            positionToUpdate = position;
        }
        return new Pair<>(statusToUpdate, positionToUpdate);
    }

    private void handleReblogEvent(@NonNull ReblogEvent reblogEvent) {
        int pos = findStatusOrReblogPositionById(reblogEvent.getStatusId());
        if (pos < 0) return;
        Status status = statuses.get(pos).asRight();
        setRebloggedForStatus(pos, status, reblogEvent.getReblog());
    }

    private void handleFavEvent(@NonNull FavoriteEvent favEvent) {
        int pos = findStatusOrReblogPositionById(favEvent.getStatusId());
        if (pos < 0) return;
        Status status = statuses.get(pos).asRight();
        setFavouriteForStatus(pos, status, favEvent.getFavourite());
    }

    private void handleStatusComposeEvent(@NonNull Status status) {
        switch (kind) {
            case HOME:
            case PUBLIC_FEDERATED:
            case PUBLIC_LOCAL:
                break;
            case USER:
            case USER_WITH_REPLIES:
                if (status.getAccount().getId().equals(hashtagOrId)) {
                    break;
                } else {
                    return;
                }
            case TAG:
            case FAVOURITES:
            case LIST:
                return;
        }
        onRefresh();
    }

    private List<Either<Placeholder, Status>> liftStatusList(List<Status> list) {
        return CollectionsKt.map(list, statusLifter);
    }

    private void updateAdapter() {
        differ.submitList(statuses.getPairedCopy());
    }

    private final ListUpdateCallback listUpdateCallback = new ListUpdateCallback() {
        @Override
        public void onInserted(int position, int count) {
            if (isAdded()) {
                adapter.notifyItemRangeInserted(position, count);
                Context context = getContext();
                // Scroll when it's very unlikely that this is a streaming update
                if (count > 0
                        && position == 0
                        && context != null
                        && (count > 1
                        || !newStreamingStatuses.contains(statuses.get(0).asRight().getId()))
                ) {
                    if (isSwipeToRefreshEnabled)
                        recyclerView.scrollBy(0, Utils.dpToPx(context, -30));
                    else
                        recyclerView.scrollToPosition(0);
                }
            }
        }

        @Override
        public void onRemoved(int position, int count) {
            adapter.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            adapter.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public void onChanged(int position, int count, Object payload) {
            adapter.notifyItemRangeChanged(position, count, payload);
        }
    };


    private final AsyncListDiffer<StatusViewData>
            differ = new AsyncListDiffer<>(listUpdateCallback,
            new AsyncDifferConfig.Builder<>(diffCallback).build());

    private final TimelineAdapter.AdapterDataSource<StatusViewData> dataSource =
            new TimelineAdapter.AdapterDataSource<StatusViewData>() {
                @Override
                public int getItemCount() {
                    return differ.getCurrentList().size();
                }

                @Override
                public StatusViewData getItemAt(int pos) {
                    return differ.getCurrentList().get(pos);
                }
            };

    private static final DiffUtil.ItemCallback<StatusViewData> diffCallback
            = new DiffUtil.ItemCallback<StatusViewData>() {

        @Override
        public boolean areItemsTheSame(StatusViewData oldItem, StatusViewData newItem) {
            return oldItem.getViewDataId() == newItem.getViewDataId();
        }

        @Override
        public boolean areContentsTheSame(StatusViewData oldItem, @NonNull StatusViewData newItem) {
            return false; //Items are different always. It allows to refresh timestamp on every view holder update
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull StatusViewData oldItem, @NonNull StatusViewData newItem) {
            if (oldItem.deepEquals(newItem)) {
                //If items are equal - update timestamp only
                List<String> payload = new ArrayList<>();
                payload.add(StatusBaseViewHolder.Key.KEY_CREATED);
                return payload;
            } else
                // If items are different - update a whole view holder
                return null;
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        startUpdateTimestamp();
    }

    /**
     * Start to update adapter every minute to refresh timestamp
     * If setting absoluteTimeView is false
     * Auto dispose observable on pause
     */
    private void startUpdateTimestamp() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false);
        if (!useAbsoluteTime) {
            Observable.interval(1, TimeUnit.MINUTES)
                    .observeOn(AndroidSchedulers.mainThread())
                    .as(autoDisposable(from(this, Lifecycle.Event.ON_PAUSE)))
                    .subscribe(
                            interval -> updateAdapter()
                    );
        }

    }

    @Override
    public void onReselect() {
        jumpToTop();
    }

    @Override
    public void refreshContent() {
        if (isAdded())
            onRefresh();
        else
            isNeedRefresh = true;
    }
}
