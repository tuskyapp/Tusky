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
import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.recyclerview.extensions.AsyncDifferConfig;
import android.support.v7.recyclerview.extensions.AsyncListDiffer;
import android.support.v7.util.DiffUtil;
import android.support.v7.util.ListUpdateCallback;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.TimelineAdapter;
import com.keylesspalace.tusky.appstore.BlockEvent;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.FavoriteEvent;
import com.keylesspalace.tusky.appstore.MuteEvent;
import com.keylesspalace.tusky.appstore.ReblogEvent;
import com.keylesspalace.tusky.appstore.StatusComposedEvent;
import com.keylesspalace.tusky.appstore.StatusDeletedEvent;
import com.keylesspalace.tusky.appstore.UnfollowEvent;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.network.TimelineCases;
import com.keylesspalace.tusky.repository.TimelineRepository;
import com.keylesspalace.tusky.util.CollectionUtil;
import com.keylesspalace.tusky.util.Either;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import at.connyduck.sparkbutton.helpers.Utils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;

public class TimelineFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener,
        StatusActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        Injectable {
    private static final String TAG = "TimelineF"; // logging tag
    private static final String KIND_ARG = "kind";
    private static final String HASHTAG_OR_ID_ARG = "hashtag_or_id";

    private static final int LOAD_AT_ONCE = 30;

    public enum Kind {
        HOME,
        PUBLIC_LOCAL,
        PUBLIC_FEDERATED,
        TAG,
        USER,
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
    public TimelineCases timelineCases;
    @Inject
    public EventHub eventHub;
    @Inject
    public TimelineRepository timeilneRepo;

    private boolean eventRegistered = false;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView nothingMessageView;

    private TimelineAdapter adapter;
    private Kind kind;
    private String hashtagOrId;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private boolean filterRemoveReplies;
    private boolean filterRemoveReblogs;
    private boolean filterRemoveRegex;
    private Matcher filterRemoveRegexMatcher;
    private boolean hideFab;
    private boolean bottomLoading;

    private long maxPlaceholderId = -1;
    private boolean didLoadEverythingBottom;

    private boolean alwaysShowSensitiveMedia;

    private CompositeDisposable disposable = new CompositeDisposable();

    @Override
    protected TimelineCases timelineCases() {
        return timelineCases;
    }

    private PairedList<Either<Placeholder, Status>, StatusViewData> statuses =
            new PairedList<>(new Function<Either<Placeholder, Status>, StatusViewData>() {
                @Override
                public StatusViewData apply(Either<Placeholder, Status> input) {
                    Status status = input.getAsRightOrNull();
                    if (status != null) {
                        return ViewDataUtils.statusToViewData(
                                status,
                                alwaysShowSensitiveMedia
                        );
                    } else {
                        Placeholder placeholder = input.getAsLeft();
                        return new StatusViewData.Placeholder(placeholder.id, false);
                    }
                }
            });

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

    private static final class Placeholder {
        final long id;

        public static Placeholder getInstance(long id) {
            return new Placeholder(id);
        }

        private Placeholder(long id) {
            this.id = id;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = Objects.requireNonNull(getArguments());
        kind = Kind.valueOf(arguments.getString(KIND_ARG));
        if (kind == Kind.TAG
                || kind == Kind.USER
                || kind == Kind.USER_WITH_REPLIES
                || kind == Kind.LIST) {
            hashtagOrId = arguments.getString(HASHTAG_OR_ID_ARG);
        }

        adapter = new TimelineAdapter(dataSource, this);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        recyclerView = rootView.findViewById(R.id.recycler_view);
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        progressBar = rootView.findViewById(R.id.progress_bar);
        nothingMessageView = rootView.findViewById(R.id.nothing_message);

        setupSwipeRefreshLayout();
        setupRecyclerView();
        updateAdapter();
        setupTimelinePreferences();
        setupNothingView();

        if (statuses.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
            bottomLoading = true;
            this.sendInitialRequest();
        } else {
            progressBar.setVisibility(View.GONE);
        }

        return rootView;
    }

    String actualTopId = null;
    String topId = null;
    String responseTopId = null;

    private void sendInitialRequest() {
        if (this.kind == Kind.HOME) {
            // Request timeline from disk to make it quick, then replace it with timeline from
            // the server to update it
            this.disposable.add(this.timeilneRepo.getStatuses(null, null, LOAD_AT_ONCE, true)
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap(statuses -> {
                        if (statuses.size() > 1) {
                            actualTopId = statuses.get(0).getId();
                            this.statuses.addAll(liftStatusList(statuses));
                            this.updateAdapter();
                            this.progressBar.setVisibility(View.GONE);
                            topId = new BigInteger(statuses.get(0).getId())
                                    .add(BigInteger.ONE).add(BigInteger.TEN).toString();
                        }
                        return this.timeilneRepo.getStatuses(topId, null, LOAD_AT_ONCE, false);
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(statuses -> {
                        responseTopId = statuses.get(0).getId();
                        Log.d("TIMELINEF", String.format("actual %s top %s response %s", actualTopId, topId, responseTopId));
                        this.statuses.clear();
                        this.onFetchTimelineSuccess(statuses, FetchEnd.TOP, -1);
                    }));
        } else {
            sendFetchTimelineRequest(null, null, FetchEnd.BOTTOM, -1);
        }
    }

    private void setupTimelinePreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);
        alwaysShowSensitiveMedia = preferences.getBoolean("alwaysShowSensitiveMedia", false);
        boolean mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true);
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        boolean useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false);
        adapter.setUseAbsoluteTime(useAbsoluteTime);

        boolean filter = preferences.getBoolean("tabFilterHomeReplies", true);
        filterRemoveReplies = kind == Kind.HOME && !filter;

        filter = preferences.getBoolean("tabFilterHomeBoosts", true);
        filterRemoveReblogs = kind == Kind.HOME && !filter;

        String regexFilter = preferences.getString("tabFilterRegex", "");
        filterRemoveRegex = (kind == Kind.HOME
                || kind == Kind.PUBLIC_LOCAL
                || kind == Kind.PUBLIC_FEDERATED)
                && !regexFilter.isEmpty();

        if (filterRemoveRegex) {
            filterRemoveRegexMatcher = Pattern.compile(regexFilter, Pattern.CASE_INSENSITIVE)
                    .matcher("");
        }
    }

    private void setupSwipeRefreshLayout() {
        Context context = swipeRefreshLayout.getContext();
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(context,
                android.R.attr.colorBackground));
    }

    private void setupRecyclerView() {
        Context context = recyclerView.getContext();
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        recyclerView.setAdapter(adapter);
    }

    private void deleteStatusById(String id) {
        for (int i = 0; i < statuses.size(); i++) {
            Either<Placeholder, Status> either = statuses.get(i);
            if (either.isRight()
                    && id.equals(either.getAsRight().getId())) {
                statuses.remove(either);
                updateAdapter();
                break;
            }
        }
        if (statuses.size() == 0) {
            nothingMessageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (jumpToTopAllowed()) {
            TabLayout layout = requireActivity().findViewById(R.id.tab_layout);
            if (layout != null) {
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
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES) {
                                String id = ((BlockEvent) event).getAccountId();
                                removeAllByAccountId(id);
                            }
                        } else if (event instanceof MuteEvent) {
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES) {
                                String id = ((MuteEvent) event).getAccountId();
                                removeAllByAccountId(id);
                            }
                        } else if (event instanceof StatusDeletedEvent) {
                            if (kind != Kind.USER && kind != Kind.USER_WITH_REPLIES) {
                                String id = ((StatusDeletedEvent) event).getStatusId();
                                deleteStatusById(id);
                            }
                        } else if (event instanceof StatusComposedEvent) {
                            Status status = ((StatusComposedEvent) event).getStatus();
                            handleStatusComposeEvent(status);
                        }
                    });
            eventRegistered = true;
        }
    }

    @Override
    public void onDestroyView() {
        if (jumpToTopAllowed()) {
            TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
            if (tabLayout != null) {
                tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
            }
        }
        super.onDestroyView();
    }

    private void setupNothingView() {
        Drawable top = AppCompatResources.getDrawable(requireContext(), R.drawable.elephant_friend_empty);
        nothingMessageView.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null);
        nothingMessageView.setVisibility(View.GONE);
    }

    @Override
    public void onRefresh() {
        sendFetchTimelineRequest(null, this.statuses.get(0).getAsRight().getId(),
                FetchEnd.TOP, -1);
    }

    @Override
    public void onReply(int position) {
        super.reply(statuses.get(position).getAsRight());
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Status status = statuses.get(position).getAsRight();
        timelineCases.reblogWithCallback(status, reblog, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull Response<Status> response) {

                if (response.isSuccessful()) {
                    setRebloggedForStatus(position, status, reblog);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(TAG, "Failed to reblog status " + status.getId(), t);
            }
        });
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
        final Status status = statuses.get(position).getAsRight();

        timelineCases.favouriteWithCallback(status, favourite, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull Response<Status> response) {

                if (response.isSuccessful()) {
                    setFavouriteForStatus(position, status, favourite);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(TAG, "Failed to favourite status " + status.getId(), t);
            }
        });
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

    @Override
    public void onMore(View view, final int position) {
        super.more(statuses.get(position).getAsRight(), view, position);
    }

    @Override
    public void onOpenReblog(int position) {
        super.openReblog(statuses.get(position).getAsRight());
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
    public void onLoadMore(int position) {
        //check bounds before accessing list,
        if (statuses.size() >= position && position > 0) {
            Status fromStatus = statuses.get(position - 1).getAsRightOrNull();
            Status toStatus = statuses.get(position + 1).getAsRightOrNull();
            if (fromStatus == null || toStatus == null) {
                Log.e(TAG, "Failed to load more at " + position + ", wrong placeholder position");
                return;
            }
            sendFetchTimelineRequest(fromStatus.getId(), toStatus.getId(), FetchEnd.MIDDLE, position);

            Placeholder placeholder = statuses.get(position).getAsLeft();
            StatusViewData newViewData = new StatusViewData.Placeholder(placeholder.id, true);
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
    public void onViewMedia(int position, int attachmentIndex, View view) {
        Status status = statuses.get(position).getAsRightOrNull();
        if (status == null) return;
        super.viewMedia(attachmentIndex, status, view);
    }

    @Override
    public void onViewThread(int position) {
        super.viewThread(statuses.get(position).getAsRight());
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "fabHide": {
                hideFab = sharedPreferences.getBoolean("fabHide", false);
                break;
            }
            case "mediaPreviewEnabled": {
                boolean enabled = sharedPreferences.getBoolean("mediaPreviewEnabled", true);
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
            case "tabFilterRegex": {
                boolean oldFilterRemoveRegex = filterRemoveRegex;
                String newFilterRemoveRegexPattern = sharedPreferences.getString("tabFilterRegex", "");
                boolean patternChanged;
                if (filterRemoveRegexMatcher != null) {
                    patternChanged = !newFilterRemoveRegexPattern.equalsIgnoreCase(filterRemoveRegexMatcher.pattern().pattern());
                } else {
                    patternChanged = !newFilterRemoveRegexPattern.isEmpty();
                }
                filterRemoveRegex = (kind == Kind.HOME || kind == Kind.PUBLIC_LOCAL || kind == Kind.PUBLIC_FEDERATED) && !newFilterRemoveRegexPattern.isEmpty();
                if (oldFilterRemoveRegex != filterRemoveRegex || patternChanged) {
                    filterRemoveRegexMatcher = Pattern.compile(newFilterRemoveRegexPattern, Pattern.CASE_INSENSITIVE).matcher("");
                    fullyRefresh();
                }
                break;
            }
            case "alwaysShowSensitiveMedia": {
                //it is ok if only newly loaded statuses are affected, no need to fully refresh
                alwaysShowSensitiveMedia = sharedPreferences.getBoolean("alwaysShowSensitiveMedia", false);
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
            Status status = iterator.next().getAsRightOrNull();
            if (status != null && status.getAccount().getId().equals(accountId)) {
                iterator.remove();
            }
        }
        updateAdapter();
    }

    private void onLoadMore() {
        if (didLoadEverythingBottom || bottomLoading) {
            return;
        }
        bottomLoading = true;

        Either<Placeholder, Status> last = statuses.get(statuses.size() - 1);
        Placeholder placeholder;
        if (last.isRight()) {
            placeholder = newPlaceholder();
            statuses.add(Either.left(placeholder));
        } else {
            placeholder = last.getAsLeft();
        }
        statuses.setPairedItem(statuses.size() - 1,
                new StatusViewData.Placeholder(placeholder.id, true));

        updateAdapter();

        String bottomId = null;
        final ListIterator<Either<Placeholder, Status>> iterator =
                this.statuses.listIterator(this.statuses.size());
        while (iterator.hasPrevious()) {
            Either<Placeholder, Status> previous = iterator.previous();
            if (previous.isRight()) {
                bottomId = previous.getAsRight().getId();
                break;
            }
        }
        sendFetchTimelineRequest(bottomId, null, FetchEnd.BOTTOM, -1);
    }

    private void fullyRefresh() {
        statuses.clear();
        updateAdapter();
        bottomLoading = true;
        sendFetchTimelineRequest(null, null, FetchEnd.BOTTOM, -1);
    }

    private boolean jumpToTopAllowed() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES;
    }

    private boolean actionButtonPresent() {
        return kind != Kind.TAG && kind != Kind.FAVOURITES &&
                getActivity() instanceof ActionButtonActivity;
    }

    private void jumpToTop() {
        layoutManager.scrollToPosition(0);
        recyclerView.stopScroll();
        scrollListener.reset();
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
                return api.accountStatuses(tagOrId, fromId, uptoId, LOAD_AT_ONCE, true, null);
            case USER_WITH_REPLIES:
                return api.accountStatuses(tagOrId, fromId, uptoId, LOAD_AT_ONCE, null, null);
            case FAVOURITES:
                return api.favourites(fromId, uptoId, LOAD_AT_ONCE);
            case LIST:
                return api.listTimeline(tagOrId, fromId, uptoId, LOAD_AT_ONCE);
        }
    }

    private void sendFetchTimelineRequest(@Nullable String fromId, @Nullable String uptoId,
                                          final FetchEnd fetchEnd, final int pos) {

        if (kind == Kind.HOME) {
            disposable.add(
                    timeilneRepo.getStatuses(fromId, uptoId, LOAD_AT_ONCE, false)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    (result) -> onFetchTimelineSuccess(result, fetchEnd, pos),
                                    (err) -> onFetchTimelineFailure(new Exception(err), fetchEnd, pos)
                            )
            );
        } else {
            Callback<List<Status>> callback = new Callback<List<Status>>() {
                @Override
                public void onResponse(@NonNull Call<List<Status>> call, @NonNull Response<List<Status>> response) {
                    if (response.isSuccessful()) {
                        onFetchTimelineSuccess(response.body(), fetchEnd, pos);
                    } else {
                        onFetchTimelineFailure(new Exception(response.message()), fetchEnd, pos);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<Status>> call, @NonNull Throwable t) {
                    onFetchTimelineFailure((Exception) t, fetchEnd, pos);
                }
            };

            Call<List<Status>> listCall = getFetchCallByTimelineType(kind, hashtagOrId, fromId, uptoId);
            callList.add(listCall);
            listCall.enqueue(callback);
        }
    }

    private void onFetchTimelineSuccess(List<Status> statuses,
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
        fulfillAnyQueuedFetches(fetchEnd);
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        if (this.statuses.size() == 0) {
            nothingMessageView.setVisibility(View.VISIBLE);
        } else {
            nothingMessageView.setVisibility(View.GONE);
        }
    }

    private void onFetchTimelineFailure(Exception exception, FetchEnd fetchEnd, int position) {
        if (isAdded()) {
            swipeRefreshLayout.setRefreshing(false);

            if (fetchEnd == FetchEnd.MIDDLE && !statuses.get(position).isRight()) {
                Placeholder placeholder = statuses.get(position).getAsLeftOrNull();
                StatusViewData newViewData;
                if (placeholder == null) {
                    placeholder = newPlaceholder();
                }
                newViewData = new StatusViewData.Placeholder(placeholder.id, false);
                statuses.setPairedItem(position, newViewData);
                updateAdapter();
            }

            Log.e(TAG, "Fetch Failure: " + exception.getMessage());
            fulfillAnyQueuedFetches(fetchEnd);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void fulfillAnyQueuedFetches(FetchEnd fetchEnd) {
        switch (fetchEnd) {
            case BOTTOM: {
                bottomLoading = false;
                break;
            }
        }
    }

    private void filterStatuses(List<Status> statuses) {
        Iterator<Status> it = statuses.iterator();
        while (it.hasNext()) {
            Status status = it.next();
            if ((status.getInReplyToId() != null && filterRemoveReplies)
                    || (status.getReblog() != null && filterRemoveReblogs)
                    || (filterRemoveRegex && (filterRemoveRegexMatcher.reset(status.getContent()).find()
                    || (!status.getSpoilerText().isEmpty() && filterRemoveRegexMatcher.reset(status.getSpoilerText()).find())))) {
                it.remove();
            }
        }
    }

    private void updateStatuses(List<Status> newStatuses, boolean fullFetch) {
        if (ListUtils.isEmpty(newStatuses)) {
            return;
        }

        List<Either<Placeholder, Status>> liftedNew = liftStatusList(newStatuses);

        if (statuses.isEmpty()) {
            statuses.addAll(liftedNew);
        } else {
            Either<Placeholder, Status> lastOfNew = liftedNew.get(newStatuses.size() - 1);
            int index = statuses.indexOf(lastOfNew);

            for (int i = 0; i < index; i++) {
                statuses.remove(0);
            }
            int newIndex = liftedNew.indexOf(statuses.get(0));
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    liftedNew.add(Either.left(newPlaceholder()));
                }
                statuses.addAll(0, liftedNew);
            } else {
                statuses.addAll(0, liftedNew.subList(0, newIndex));
            }
        }
        updateAdapter();
    }

    private void addItems(List<Status> newStatuses) {
        if (ListUtils.isEmpty(newStatuses)) {
            return;
        }
        Status last = null;
        for (int i = statuses.size() - 1; i >= 0; i--) {
            if (statuses.get(i).isRight()) {
                last = statuses.get(i).getAsRight();
                break;
            }
        }
        // I was about to replace findStatus with indexOf but it is incorrect to compare value
        // types by ID anyway and we should change equals() for Status, I think, so this makes sense
        if (last != null && !findStatus(newStatuses, last.getId())) {
            statuses.addAll(liftStatusList(newStatuses));
            updateAdapter();
        }
    }

    private void replacePlaceholderWithStatuses(List<Status> newStatuses, boolean fullFetch, int pos) {
        Status status = statuses.get(pos).getAsRightOrNull();
        if (status == null) {
            statuses.remove(pos);
        }

        if (ListUtils.isEmpty(newStatuses)) {
            updateAdapter();
            return;
        }

        List<Either<Placeholder, Status>> liftedNew = liftStatusList(newStatuses);

        if (fullFetch) {
            liftedNew.add(Either.left(newPlaceholder()));
        }

        statuses.addAll(pos, liftedNew);
        updateAdapter();

    }

    private static boolean findStatus(List<Status> statuses, String id) {
        for (Status status : statuses) {
            if (status.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private int findStatusOrReblogPositionById(@NonNull String statusId) {
        for (int i = 0; i < statuses.size(); i++) {
            Status status = statuses.get(i).getAsRightOrNull();
            if (status != null
                    && (statusId.equals(status.getId())
                    || (status.getReblog() != null
                    && statusId.equals(status.getReblog().getId())))) {
                return i;
            }
        }
        return -1;
    }

    private final Function<Status, Either<Placeholder, Status>> statusLifter =
            Either::right;

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
            int foundPos = statuses.indexOf(Either.<Placeholder, Status>right(status));
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
        Status status = statuses.get(pos).getAsRight();
        setRebloggedForStatus(pos, status, reblogEvent.getReblog());
    }

    private void handleFavEvent(@NonNull FavoriteEvent favEvent) {
        int pos = findStatusOrReblogPositionById(favEvent.getStatusId());
        if (pos < 0) return;
        Status status = statuses.get(pos).getAsRight();
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
        return CollectionUtil.map(list, statusLifter);
    }

    private Placeholder newPlaceholder() {
        Placeholder placeholder = Placeholder.getInstance(maxPlaceholderId);
        maxPlaceholderId--;
        return placeholder;
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
                if (position == 0 && context != null) {
                    recyclerView.scrollBy(0, Utils.dpToPx(context, -30));
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
        public boolean areContentsTheSame(StatusViewData oldItem, StatusViewData newItem) {
            return oldItem.deepEquals(newItem);
        }
    };
}
