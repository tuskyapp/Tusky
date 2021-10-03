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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.arch.core.util.Function;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.util.Pair;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.NotificationsAdapter;
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder;
import com.keylesspalace.tusky.appstore.BlockEvent;
import com.keylesspalace.tusky.appstore.BookmarkEvent;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.FavoriteEvent;
import com.keylesspalace.tusky.appstore.PinEvent;
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent;
import com.keylesspalace.tusky.appstore.ReblogEvent;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Poll;
import com.keylesspalace.tusky.entity.Relationship;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.interfaces.ReselectableFragment;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.settings.PrefKeys;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.Either;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.NotificationTypeConverterKt;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.BackgroundMessageView;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;
import com.keylesspalace.tusky.viewdata.AttachmentViewData;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import at.connyduck.sparkbutton.helpers.Utils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;

import static autodispose2.AutoDispose.autoDisposable;
import static autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider.from;
import static com.keylesspalace.tusky.util.StringUtils.isLessThan;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener,
        StatusActionListener,
        NotificationsAdapter.NotificationActionListener,
        AccountActionListener,
        Injectable, ReselectableFragment {
    private static final String TAG = "NotificationF"; // logging tag

    private static final int LOAD_AT_ONCE = 30;
    private int maxPlaceholderId = 0;

    private final Set<Notification.Type> notificationFilter = new HashSet<>();

    private final CompositeDisposable disposables = new CompositeDisposable();

    private enum FetchEnd {
        TOP,
        BOTTOM,
        MIDDLE
    }

    /**
     * Placeholder for the notificationsEnabled. Consider moving to the separate class to hide constructor
     * and reuse in different places as needed.
     */
    private static final class Placeholder {
        final long id;

        public static Placeholder getInstance(long id) {
            return new Placeholder(id);
        }

        private Placeholder(long id) {
            this.id = id;
        }
    }

    @Inject
    AccountManager accountManager;
    @Inject
    EventHub eventHub;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private BackgroundMessageView statusView;
    private AppBarLayout appBarOptions;

    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private NotificationsAdapter adapter;
    private Button buttonFilter;
    private boolean hideFab;
    private boolean topLoading;
    private boolean bottomLoading;
    private String bottomId;
    private boolean alwaysShowSensitiveMedia;
    private boolean alwaysOpenSpoiler;
    private boolean showNotificationsFilter;
    private boolean showingError;

    // Each element is either a Notification for loading data or a Placeholder
    private final PairedList<Either<Placeholder, Notification>, NotificationViewData> notifications
            = new PairedList<>(new Function<Either<Placeholder, Notification>, NotificationViewData>() {
        @Override
        public NotificationViewData apply(Either<Placeholder, Notification> input) {
            if (input.isRight()) {
                Notification notification = input.asRight()
                        .rewriteToStatusTypeIfNeeded(accountManager.getActiveAccount().getAccountId());

                return ViewDataUtils.notificationToViewData(
                        notification,
                        alwaysShowSensitiveMedia,
                        alwaysOpenSpoiler
                );
            } else {
                return new NotificationViewData.Placeholder(input.asLeft().id, false);
            }
        }
    });

    public static NotificationsFragment newInstance() {
        NotificationsFragment fragment = new NotificationsFragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_timeline_notifications, container, false);

        @NonNull Context context = inflater.getContext(); // from inflater to silence warning
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        boolean showNotificationsFilterSetting = preferences.getBoolean("showNotificationsFilter", true);
        //Clear notifications on filter visibility change to force refresh
        if (showNotificationsFilterSetting != showNotificationsFilter)
            notifications.clear();
        showNotificationsFilter = showNotificationsFilterSetting;

        // Setup the SwipeRefreshLayout.
        swipeRefreshLayout = rootView.findViewById(R.id.swipeRefreshLayout);
        recyclerView = rootView.findViewById(R.id.recyclerView);
        progressBar = rootView.findViewById(R.id.progressBar);
        statusView = rootView.findViewById(R.id.statusView);
        appBarOptions = rootView.findViewById(R.id.appBarOptions);

        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue);

        loadNotificationsFilter();

        // Setup the RecyclerView.
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAccessibilityDelegateCompat(
                new ListStatusAccessibilityDelegate(recyclerView, this, (pos) -> {
                    NotificationViewData notification = notifications.getPairedItemOrNull(pos);
                    // We support replies only for now
                    if (notification instanceof NotificationViewData.Concrete) {
                        return ((NotificationViewData.Concrete) notification).getStatusViewData();
                    } else {
                        return null;
                    }
                }));

        recyclerView.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        StatusDisplayOptions statusDisplayOptions = new StatusDisplayOptions(
                preferences.getBoolean("animateGifAvatars", false),
                accountManager.getActiveAccount().getMediaPreviewEnabled(),
                preferences.getBoolean("absoluteTimeView", false),
                preferences.getBoolean("showBotOverlay", true),
                preferences.getBoolean("useBlurhash", true),
                CardViewMode.NONE,
                preferences.getBoolean("confirmReblogs", true),
                preferences.getBoolean("confirmFavourites", true),
                preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
                preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        );

        adapter = new NotificationsAdapter(accountManager.getActiveAccount().getAccountId(),
                dataSource, statusDisplayOptions, this, this, this);
        alwaysShowSensitiveMedia = accountManager.getActiveAccount().getAlwaysShowSensitiveMedia();
        alwaysOpenSpoiler = accountManager.getActiveAccount().getAlwaysOpenSpoiler();
        recyclerView.setAdapter(adapter);

        topLoading = false;
        bottomLoading = false;
        bottomId = null;

        updateAdapter();

        Button buttonClear = rootView.findViewById(R.id.buttonClear);
        buttonClear.setOnClickListener(v -> confirmClearNotifications());
        buttonFilter = rootView.findViewById(R.id.buttonFilter);
        buttonFilter.setOnClickListener(v -> showFilterMenu());

        if (notifications.isEmpty()) {
            swipeRefreshLayout.setEnabled(false);
            sendFetchNotificationsRequest(null, null, FetchEnd.BOTTOM, -1);
        } else {
            progressBar.setVisibility(View.GONE);
        }

        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        updateFilterVisibility();

        return rootView;
    }

    private void updateFilterVisibility() {
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) swipeRefreshLayout.getLayoutParams();
        if (showNotificationsFilter && !showingError) {
            appBarOptions.setExpanded(true, false);
            appBarOptions.setVisibility(View.VISIBLE);
            //Set content behaviour to hide filter on scroll
            params.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        } else {
            appBarOptions.setExpanded(false, false);
            appBarOptions.setVisibility(View.GONE);
            //Clear behaviour to hide app bar
            params.setBehavior(null);
        }
    }

    private void confirmClearNotifications() {
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.notification_clear_text)
                .setPositiveButton(android.R.string.ok, (DialogInterface dia, int which) -> clearNotifications())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        if (activity == null) throw new AssertionError("Activity is null");

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then.
         * Use a modified scroll listener that both loads more notificationsEnabled as it goes, and hides
         * the compose button on down-scroll. */
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        hideFab = preferences.getBoolean("fabHide", false);
        scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
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
                NotificationsFragment.this.onLoadMore();
            }
        };

        recyclerView.addOnScrollListener(scrollListener);

        eventHub.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(event -> {
                    if (event instanceof FavoriteEvent) {
                        setFavouriteForStatus(((FavoriteEvent) event).getStatusId(), ((FavoriteEvent) event).getFavourite());
                    } else if (event instanceof BookmarkEvent) {
                        setBookmarkForStatus(((BookmarkEvent) event).getStatusId(), ((BookmarkEvent) event).getBookmark());
                    } else if (event instanceof ReblogEvent) {
                        setReblogForStatus(((ReblogEvent) event).getStatusId(), ((ReblogEvent) event).getReblog());
                    } else if (event instanceof PinEvent) {
                        setPinForStatus(((PinEvent) event).getStatusId(), ((PinEvent) event).getPinned());
                    } else if (event instanceof BlockEvent) {
                        removeAllByAccountId(((BlockEvent) event).getAccountId());
                    } else if (event instanceof PreferenceChangedEvent) {
                        onPreferenceChanged(((PreferenceChangedEvent) event).getPreferenceKey());
                    }
                });
    }

    @Override
    public void onRefresh() {
        this.statusView.setVisibility(View.GONE);
        this.showingError = false;
        Either<Placeholder, Notification> first = CollectionsKt.firstOrNull(this.notifications);
        String topId;
        if (first != null && first.isRight()) {
            topId = first.asRight().getId();
        } else {
            topId = null;
        }
        sendFetchNotificationsRequest(null, topId, FetchEnd.TOP, -1);
    }

    @Override
    public void onReply(int position) {
        super.reply(notifications.get(position).asRight().getStatus());
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Notification notification = notifications.get(position).asRight();
        final Status status = notification.getStatus();
        Objects.requireNonNull(status, "Reblog on notification without status");
        timelineCases.reblog(status.getId(), reblog)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        (newStatus) -> setReblogForStatus(status.getId(), reblog),
                        (t) -> Log.d(getClass().getSimpleName(),
                                "Failed to reblog status: " + status.getId(), t)
                );
    }

    private void setReblogForStatus(String statusId, boolean reblog) {
        updateStatus(statusId, (s) -> {
            s.setReblogged(reblog);
            return s;
        });
    }

    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Notification notification = notifications.get(position).asRight();
        final Status status = notification.getStatus();

        timelineCases.favourite(status.getId(), favourite)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        (newStatus) -> setFavouriteForStatus(status.getId(), favourite),
                        (t) -> Log.d(getClass().getSimpleName(),
                                "Failed to favourite status: " + status.getId(), t)
                );
    }

    private void setFavouriteForStatus(String statusId, boolean favourite) {
        updateStatus(statusId, (s) -> {
            s.setFavourited(favourite);
            return s;
        });
    }

    @Override
    public void onBookmark(final boolean bookmark, final int position) {
        final Notification notification = notifications.get(position).asRight();
        final Status status = notification.getStatus();

        timelineCases.bookmark(status.getActionableId(), bookmark)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        (newStatus) -> setBookmarkForStatus(status.getId(), bookmark),
                        (t) -> Log.d(getClass().getSimpleName(),
                                "Failed to bookmark status: " + status.getId(), t)
                );
    }

    private void setBookmarkForStatus(String statusId, boolean bookmark) {
        updateStatus(statusId, (s) -> {
            s.setBookmarked(bookmark);
            return s;
        });
    }

    public void onVoteInPoll(int position, @NonNull List<Integer> choices) {
        final Notification notification = notifications.get(position).asRight();
        final Status status = notification.getStatus().getActionableStatus();
        timelineCases.voteInPoll(status.getId(), status.getPoll().getId(), choices)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this)))
                .subscribe(
                        (newPoll) -> setVoteForPoll(status, newPoll),
                        (t) -> Log.d(TAG,
                                "Failed to vote in poll: " + status.getId(), t)
                );
    }

    private void setVoteForPoll(Status status, Poll poll) {
        updateStatus(status.getId(), (s) -> s.copyWithPoll(poll));
    }

    @Override
    public void onMore(@NonNull View view, int position) {
        Notification notification = notifications.get(position).asRight();
        super.more(notification.getStatus(), view, position);
    }

    @Override
    public void onViewMedia(int position, int attachmentIndex, @Nullable View view) {
        Notification notification = notifications.get(position).asRightOrNull();
        if (notification == null || notification.getStatus() == null) return;
        Status status = notification.getStatus();
        super.viewMedia(attachmentIndex, AttachmentViewData.list(status), view);
    }

    @Override
    public void onViewThread(int position) {
        Notification notification = notifications.get(position).asRight();
        Status status = notification.getStatus();
        if (status == null) return;
        ;
        super.viewThread(status.getActionableId(), status.getActionableStatus().getUrl());
    }

    @Override
    public void onOpenReblog(int position) {
        Notification notification = notifications.get(position).asRight();
        onViewAccount(notification.getAccount().getId());
    }

    @Override
    public void onExpandedChange(boolean expanded, int position) {
        updateViewDataAt(position, (vd) -> vd.copyWithExpanded(expanded));
    }

    @Override
    public void onContentHiddenChange(boolean isShowing, int position) {
        updateViewDataAt(position, (vd) -> vd.copyWithShowingContent(isShowing));
    }

    private void setPinForStatus(String statusId, boolean pinned) {
        updateStatus(statusId, status -> {
            status.copyWithPinned(pinned);
            return status;
        });
    }

    @Override
    public void onLoadMore(int position) {
        //check bounds before accessing list,
        if (notifications.size() >= position && position > 0) {
            Notification previous = notifications.get(position - 1).asRightOrNull();
            Notification next = notifications.get(position + 1).asRightOrNull();
            if (previous == null || next == null) {
                Log.e(TAG, "Failed to load more, invalid placeholder position: " + position);
                return;
            }
            sendFetchNotificationsRequest(previous.getId(), next.getId(), FetchEnd.MIDDLE, position);
            Placeholder placeholder = notifications.get(position).asLeft();
            NotificationViewData notificationViewData =
                    new NotificationViewData.Placeholder(placeholder.id, true);
            notifications.setPairedItem(position, notificationViewData);
            updateAdapter();
        } else {
            Log.d(TAG, "error loading more");
        }
    }

    @Override
    public void onContentCollapsedChange(boolean isCollapsed, int position) {
        updateViewDataAt(position, (vd) -> vd.copyWIthCollapsed(isCollapsed));
        ;
    }

    private void updateStatus(String statusId, Function<Status, Status> mapper) {
        int index = CollectionsKt.indexOfFirst(this.notifications, (s) -> s.isRight() &&
                s.asRight().getStatus() != null &&
                s.asRight().getStatus().getId().equals(statusId));
        if (index == -1) return;

        // We have quite some graph here:
        //
        //      Notification --------> Status
        //                                ^
        //                                |
        //                             StatusViewData
        //                                ^
        //                                |
        //      NotificationViewData -----+
        //
        // So if we have "new" status we need to update all references to be sure that data is
        // up-to-date:
        // 1. update status
        // 2. update notification
        // 3. update statusViewData
        // 4. update notificationViewData

        Status oldStatus = notifications.get(index).asRight().getStatus();
        NotificationViewData.Concrete oldViewData =
                (NotificationViewData.Concrete) this.notifications.getPairedItem(index);
        Status newStatus = mapper.apply(oldStatus);
        Notification newNotification = this.notifications.get(index).asRight()
                .copyWithStatus(newStatus);
        StatusViewData.Concrete newStatusViewData =
                Objects.requireNonNull(oldViewData.getStatusViewData()).copyWithStatus(newStatus);
        NotificationViewData.Concrete newViewData = oldViewData.copyWithStatus(newStatusViewData);

        notifications.set(index, new Either.Right<>(newNotification));
        notifications.setPairedItem(index, newViewData);

        updateAdapter();
    }

    private void updateViewDataAt(int position,
                                  Function<StatusViewData.Concrete, StatusViewData.Concrete> mapper) {
        if (position < 0 || position >= notifications.size()) {
            String message = String.format(
                    Locale.getDefault(),
                    "Tried to access out of bounds status position: %d of %d",
                    position,
                    notifications.size() - 1
            );
            Log.e(TAG, message);
            return;
        }
        NotificationViewData someViewData = this.notifications.getPairedItem(position);
        if (!(someViewData instanceof NotificationViewData.Concrete)) {
            return;
        }
        NotificationViewData.Concrete oldViewData = (NotificationViewData.Concrete) someViewData;
        StatusViewData.Concrete oldStatusViewData = oldViewData.getStatusViewData();
        if (oldStatusViewData == null) return;

        NotificationViewData.Concrete newViewData =
                oldViewData.copyWithStatus(mapper.apply(oldStatusViewData));
        notifications.setPairedItem(position, newViewData);

        updateAdapter();
    }

    @Override
    public void onNotificationContentCollapsedChange(boolean isCollapsed, int position) {
        onContentCollapsedChange(isCollapsed, position);
    }

    private void clearNotifications() {
        //Cancel all ongoing requests
        swipeRefreshLayout.setRefreshing(false);
        resetNotificationsLoad();

        //Show friend elephant
        this.statusView.setVisibility(View.VISIBLE);
        this.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null);
        updateFilterVisibility();

        //Update adapter
        updateAdapter();

        //Execute clear notifications request
        mastodonApi.clearNotifications()
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        response -> {
                            // nothing to do
                        },
                        throwable -> {
                            //Reload notifications on failure
                            fullyRefreshWithProgressBar(true);
                        });
    }

    private void resetNotificationsLoad() {
        disposables.clear();
        bottomLoading = false;
        topLoading = false;

        //Disable load more
        bottomId = null;

        //Clear exists notifications
        notifications.clear();
    }


    private void showFilterMenu() {
        List<Notification.Type> notificationsList = Notification.Type.Companion.getAsList();
        List<String> list = new ArrayList<>();
        for (Notification.Type type : notificationsList) {
            list.add(getNotificationText(type));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, list);
        PopupWindow window = new PopupWindow(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.notifications_filter, (ViewGroup) getView(), false);
        final ListView listView = view.findViewById(R.id.listView);
        view.findViewById(R.id.buttonApply)
                .setOnClickListener(v -> {
                    SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
                    Set<Notification.Type> excludes = new HashSet<>();
                    for (int i = 0; i < notificationsList.size(); i++) {
                        if (!checkedItems.get(i, false))
                            excludes.add(notificationsList.get(i));
                    }
                    window.dismiss();
                    applyFilterChanges(excludes);

                });

        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        for (int i = 0; i < notificationsList.size(); i++) {
            if (!notificationFilter.contains(notificationsList.get(i)))
                listView.setItemChecked(i, true);
        }
        window.setContentView(view);
        window.setFocusable(true);
        window.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        window.showAsDropDown(buttonFilter);

    }

    private String getNotificationText(Notification.Type type) {
        switch (type) {
            case MENTION:
                return getString(R.string.notification_mention_name);
            case FAVOURITE:
                return getString(R.string.notification_favourite_name);
            case REBLOG:
                return getString(R.string.notification_boost_name);
            case FOLLOW:
                return getString(R.string.notification_follow_name);
            case FOLLOW_REQUEST:
                return getString(R.string.notification_follow_request_name);
            case POLL:
                return getString(R.string.notification_poll_name);
            case STATUS:
                return getString(R.string.notification_subscription_name);
            default:
                return "Unknown";
        }
    }

    private void applyFilterChanges(Set<Notification.Type> newSet) {
        List<Notification.Type> notifications = Notification.Type.Companion.getAsList();
        boolean isChanged = false;
        for (Notification.Type type : notifications) {
            if (notificationFilter.contains(type) && !newSet.contains(type)) {
                notificationFilter.remove(type);
                isChanged = true;
            } else if (!notificationFilter.contains(type) && newSet.contains(type)) {
                notificationFilter.add(type);
                isChanged = true;
            }
        }
        if (isChanged) {
            saveNotificationsFilter();
            fullyRefreshWithProgressBar(true);
        }

    }

    private void loadNotificationsFilter() {
        AccountEntity account = accountManager.getActiveAccount();
        if (account != null) {
            notificationFilter.clear();
            notificationFilter.addAll(NotificationTypeConverterKt.deserialize(
                    account.getNotificationsFilter()));
        }
    }

    private void saveNotificationsFilter() {
        AccountEntity account = accountManager.getActiveAccount();
        if (account != null) {
            account.setNotificationsFilter(NotificationTypeConverterKt.serialize(notificationFilter));
            accountManager.saveAccount(account);
        }
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
    public void onMute(boolean mute, String id, int position, boolean notifications) {
        // No muting from notifications yet
    }

    @Override
    public void onBlock(boolean block, String id, int position) {
        // No blocking from notifications yet
    }

    @Override
    public void onRespondToFollowRequest(boolean accept, String id, int position) {
        Single<Relationship> request = accept ?
                mastodonApi.authorizeFollowRequest(id) :
                mastodonApi.rejectFollowRequest(id);
        request.observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        (relationship) -> fullyRefreshWithProgressBar(true),
                        (error) -> Log.e(TAG, String.format("Failed to %s account id %s", accept ? "accept" : "reject", id))
                );
    }

    @Override
    public void onViewStatusForNotificationId(String notificationId) {
        for (Either<Placeholder, Notification> either : notifications) {
            Notification notification = either.asRightOrNull();
            if (notification != null && notification.getId().equals(notificationId)) {
                Status status = notification.getStatus();
                if (status != null) {
                    super.viewThread(status.getActionableId(), status.getActionableStatus().getUrl());
                    return;
                }
            }
        }
        Log.w(TAG, "Didn't find a notification for ID: " + notificationId);
    }

    private void onPreferenceChanged(String key) {
        switch (key) {
            case "fabHide": {
                hideFab = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("fabHide", false);
                break;
            }
            case "mediaPreviewEnabled": {
                boolean enabled = accountManager.getActiveAccount().getMediaPreviewEnabled();
                if (enabled != adapter.isMediaPreviewEnabled()) {
                    adapter.setMediaPreviewEnabled(enabled);
                    fullyRefresh();
                }
                break;
            }
            case "showNotificationsFilter": {
                if (isAdded()) {
                    showNotificationsFilter = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("showNotificationsFilter", true);
                    updateFilterVisibility();
                    fullyRefreshWithProgressBar(true);
                }
                break;
            }
        }
    }

    @Override
    public void removeItem(int position) {
        notifications.remove(position);
        updateAdapter();
    }

    private void removeAllByAccountId(String accountId) {
        // using iterator to safely remove items while iterating
        Iterator<Either<Placeholder, Notification>> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Either<Placeholder, Notification> notification = iterator.next();
            Notification maybeNotification = notification.asRightOrNull();
            if (maybeNotification != null && maybeNotification.getAccount().getId().equals(accountId)) {
                iterator.remove();
            }
        }
        updateAdapter();
    }

    private void onLoadMore() {
        if (bottomId == null) {
            // already loaded everything
            return;
        }

        // Check for out-of-bounds when loading
        // This is required to allow full-timeline reloads of collapsible statuses when the settings
        // change.
        if (notifications.size() > 0) {
            Either<Placeholder, Notification> last = notifications.get(notifications.size() - 1);
            if (last.isRight()) {
                final Placeholder placeholder = newPlaceholder();
                notifications.add(new Either.Left<>(placeholder));
                NotificationViewData viewData =
                        new NotificationViewData.Placeholder(placeholder.id, true);
                notifications.setPairedItem(notifications.size() - 1, viewData);
                updateAdapter();
            }
        }

        sendFetchNotificationsRequest(bottomId, null, FetchEnd.BOTTOM, -1);
    }

    private Placeholder newPlaceholder() {
        Placeholder placeholder = Placeholder.getInstance(maxPlaceholderId);
        maxPlaceholderId--;
        return placeholder;
    }

    private void jumpToTop() {
        if (isAdded()) {
            appBarOptions.setExpanded(true, false);
            layoutManager.scrollToPosition(0);
            scrollListener.reset();
        }
    }

    private void sendFetchNotificationsRequest(String fromId, String uptoId,
                                               final FetchEnd fetchEnd, final int pos) {
        /* If there is a fetch already ongoing, record however many fetches are requested and
         * fulfill them after it's complete. */
        if (fetchEnd == FetchEnd.TOP && topLoading) {
            return;
        }
        if (fetchEnd == FetchEnd.BOTTOM && bottomLoading) {
            return;
        }
        if (fetchEnd == FetchEnd.TOP) {
            topLoading = true;
        }
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = true;
        }

        Disposable notificationCall = mastodonApi.notifications(fromId, uptoId, LOAD_AT_ONCE, showNotificationsFilter ? notificationFilter : null)
                .observeOn(AndroidSchedulers.mainThread())
                .to(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(
                        response -> {
                            if (response.isSuccessful()) {
                                String linkHeader = response.headers().get("Link");
                                onFetchNotificationsSuccess(response.body(), linkHeader, fetchEnd, pos);
                            } else {
                                onFetchNotificationsFailure(new Exception(response.message()), fetchEnd, pos);
                            }
                        },
                        throwable -> onFetchNotificationsFailure(throwable, fetchEnd, pos));
        disposables.add(notificationCall);
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, String linkHeader,
                                             FetchEnd fetchEnd, int pos) {
        List<HttpHeaderLink> links = HttpHeaderLink.parse(linkHeader);
        HttpHeaderLink next = HttpHeaderLink.findByRelationType(links, "next");
        String fromId = null;
        if (next != null) {
            fromId = next.uri.getQueryParameter("max_id");
        }

        switch (fetchEnd) {
            case TOP: {
                update(notifications, this.notifications.isEmpty() ? fromId : null);
                break;
            }
            case MIDDLE: {
                replacePlaceholderWithNotifications(notifications, pos);
                break;
            }
            case BOTTOM: {

                if (!this.notifications.isEmpty()
                        && !this.notifications.get(this.notifications.size() - 1).isRight()) {
                    this.notifications.remove(this.notifications.size() - 1);
                    updateAdapter();
                }

                if (adapter.getItemCount() > 1) {
                    addItems(notifications, fromId);
                } else {
                    update(notifications, fromId);
                }

                break;
            }
        }

        saveNewestNotificationId(notifications);

        if (fetchEnd == FetchEnd.TOP) {
            topLoading = false;
        }
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false;
        }

        if (notifications.size() == 0 && adapter.getItemCount() == 0) {
            this.statusView.setVisibility(View.VISIBLE);
            this.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null);
        } else {
            swipeRefreshLayout.setEnabled(true);
        }
        updateFilterVisibility();
        swipeRefreshLayout.setRefreshing(false);
        progressBar.setVisibility(View.GONE);
    }

    private void onFetchNotificationsFailure(Throwable throwable, FetchEnd fetchEnd, int position) {
        swipeRefreshLayout.setRefreshing(false);
        if (fetchEnd == FetchEnd.MIDDLE && !notifications.get(position).isRight()) {
            Placeholder placeholder = notifications.get(position).asLeft();
            NotificationViewData placeholderVD =
                    new NotificationViewData.Placeholder(placeholder.id, false);
            notifications.setPairedItem(position, placeholderVD);
            updateAdapter();
        } else if (this.notifications.isEmpty()) {
            this.statusView.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setEnabled(false);
            this.showingError = true;
            if (throwable instanceof IOException) {
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
            updateFilterVisibility();
        }
        Log.e(TAG, "Fetch failure: " + throwable.getMessage());

        if (fetchEnd == FetchEnd.TOP) {
            topLoading = false;
        }
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false;
        }

        progressBar.setVisibility(View.GONE);
    }

    private void saveNewestNotificationId(List<Notification> notifications) {

        AccountEntity account = accountManager.getActiveAccount();
        if (account != null) {
            String lastNotificationId = account.getLastNotificationId();

            for (Notification noti : notifications) {
                if (isLessThan(lastNotificationId, noti.getId())) {
                    lastNotificationId = noti.getId();
                }
            }

            if (!account.getLastNotificationId().equals(lastNotificationId)) {
                Log.d(TAG, "saving newest noti id: " + lastNotificationId);
                account.setLastNotificationId(lastNotificationId);
                accountManager.saveAccount(account);
            }
        }
    }

    private void update(@Nullable List<Notification> newNotifications, @Nullable String fromId) {
        if (ListUtils.isEmpty(newNotifications)) {
            updateAdapter();
            return;
        }
        if (fromId != null) {
            bottomId = fromId;
        }
        List<Either<Placeholder, Notification>> liftedNew =
                liftNotificationList(newNotifications);
        if (notifications.isEmpty()) {
            notifications.addAll(liftedNew);
        } else {
            int index = notifications.indexOf(liftedNew.get(newNotifications.size() - 1));
            for (int i = 0; i < index; i++) {
                notifications.remove(0);
            }

            int newIndex = liftedNew.indexOf(notifications.get(0));
            if (newIndex == -1) {
                if (index == -1 && liftedNew.size() >= LOAD_AT_ONCE) {
                    liftedNew.add(new Either.Left<>(newPlaceholder()));
                }
                notifications.addAll(0, liftedNew);
            } else {
                notifications.addAll(0, liftedNew.subList(0, newIndex));
            }
        }
        updateAdapter();
    }

    private void addItems(List<Notification> newNotifications, @Nullable String fromId) {
        bottomId = fromId;
        if (ListUtils.isEmpty(newNotifications)) {
            return;
        }
        int end = notifications.size();
        List<Either<Placeholder, Notification>> liftedNew = liftNotificationList(newNotifications);
        Either<Placeholder, Notification> last = notifications.get(end - 1);
        if (last != null && liftedNew.indexOf(last) == -1) {
            notifications.addAll(liftedNew);
            updateAdapter();
        }
    }

    private void replacePlaceholderWithNotifications(List<Notification> newNotifications, int pos) {
        // Remove placeholder
        notifications.remove(pos);

        if (ListUtils.isEmpty(newNotifications)) {
            updateAdapter();
            return;
        }

        List<Either<Placeholder, Notification>> liftedNew = liftNotificationList(newNotifications);

        // If we fetched less posts than in the limit, it means that the hole is not filled
        // If we fetched at least as much it means that there are more posts to load and we should
        // insert new placeholder
        if (newNotifications.size() >= LOAD_AT_ONCE) {
            liftedNew.add(new Either.Left<>(newPlaceholder()));
        }

        notifications.addAll(pos, liftedNew);
        updateAdapter();
    }

    private final Function1<Notification, Either<Placeholder, Notification>> notificationLifter =
            Either.Right::new;

    private List<Either<Placeholder, Notification>> liftNotificationList(List<Notification> list) {
        return CollectionsKt.map(list, notificationLifter);
    }

    private void fullyRefreshWithProgressBar(boolean isShow) {
        resetNotificationsLoad();
        if (isShow) {
            progressBar.setVisibility(View.VISIBLE);
            statusView.setVisibility(View.GONE);
        }
        updateAdapter();
        sendFetchNotificationsRequest(null, null, FetchEnd.TOP, -1);
    }

    private void fullyRefresh() {
        fullyRefreshWithProgressBar(false);
    }

    @Nullable
    private Pair<Integer, Notification> findReplyPosition(@NonNull String statusId) {
        for (int i = 0; i < notifications.size(); i++) {
            Notification notification = notifications.get(i).asRightOrNull();
            if (notification != null
                    && notification.getStatus() != null
                    && notification.getType() == Notification.Type.MENTION
                    && (statusId.equals(notification.getStatus().getId())
                    || (notification.getStatus().getReblog() != null
                    && statusId.equals(notification.getStatus().getReblog().getId())))) {
                return new Pair<>(i, notification);
            }
        }
        return null;
    }

    private void updateAdapter() {
        differ.submitList(notifications.getPairedCopy());
    }

    private final ListUpdateCallback listUpdateCallback = new ListUpdateCallback() {
        @Override
        public void onInserted(int position, int count) {
            if (isAdded()) {
                adapter.notifyItemRangeInserted(position, count);
                Context context = getContext();
                // scroll up when new items at the top are loaded while being at the start
                // https://github.com/tuskyapp/Tusky/pull/1905#issuecomment-677819724
                if (position == 0 && context != null && adapter.getItemCount() != count) {
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

    private final AsyncListDiffer<NotificationViewData>
            differ = new AsyncListDiffer<>(listUpdateCallback,
            new AsyncDifferConfig.Builder<>(diffCallback).build());

    private final NotificationsAdapter.AdapterDataSource<NotificationViewData> dataSource =
            new NotificationsAdapter.AdapterDataSource<NotificationViewData>() {
                @Override
                public int getItemCount() {
                    return differ.getCurrentList().size();
                }

                @Override
                public NotificationViewData getItemAt(int pos) {
                    return differ.getCurrentList().get(pos);
                }
            };

    private static final DiffUtil.ItemCallback<NotificationViewData> diffCallback
            = new DiffUtil.ItemCallback<NotificationViewData>() {

        @Override
        public boolean areItemsTheSame(NotificationViewData oldItem, NotificationViewData newItem) {
            return oldItem.getViewDataId() == newItem.getViewDataId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull NotificationViewData oldItem, @NonNull NotificationViewData newItem) {
            return false;
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull NotificationViewData oldItem, @NonNull NotificationViewData newItem) {
            if (oldItem.deepEquals(newItem)) {
                //If items are equal - update timestamp only
                return Collections.singletonList(StatusBaseViewHolder.Key.KEY_CREATED);
            } else
                // If items are different - update a whole view holder
                return null;
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        String rawAccountNotificationFilter = accountManager.getActiveAccount().getNotificationsFilter();
        Set<Notification.Type> accountNotificationFilter = NotificationTypeConverterKt.deserialize(rawAccountNotificationFilter);
        if (!notificationFilter.equals(accountNotificationFilter)) {
            loadNotificationsFilter();
            fullyRefreshWithProgressBar(true);
        }
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
                    .to(autoDisposable(from(this, Lifecycle.Event.ON_PAUSE)))
                    .subscribe(
                            interval -> updateAdapter()
                    );
        }

    }

    @Override
    public void onReselect() {
        jumpToTop();
    }
}
