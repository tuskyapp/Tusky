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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.keylesspalace.tusky.MainActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.NotificationsAdapter;
import com.keylesspalace.tusky.appstore.BlockEvent;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.FavoriteEvent;
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent;
import com.keylesspalace.tusky.appstore.ReblogEvent;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.network.TimelineCases;
import com.keylesspalace.tusky.util.CollectionUtil;
import com.keylesspalace.tusky.util.Either;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.BackgroundMessageView;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.core.util.Pair;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.reactivex.android.schedulers.AndroidSchedulers;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.keylesspalace.tusky.util.StringUtils.isLessThan;
import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener,
        StatusActionListener,
        NotificationsAdapter.NotificationActionListener,
        Injectable {
    private static final String TAG = "NotificationF"; // logging tag

    private static final int LOAD_AT_ONCE = 30;

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
        private static final Placeholder INSTANCE = new Placeholder();

        public static Placeholder getInstance() {
            return INSTANCE;
        }

        private Placeholder() {
        }
    }

    @Inject
    public TimelineCases timelineCases;
    @Inject
    AccountManager accountManager;
    @Inject
    EventHub eventHub;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private BackgroundMessageView statusView;

    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private NotificationsAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private boolean hideFab;
    private boolean topLoading;
    private boolean bottomLoading;
    private String bottomId;
    private boolean alwaysShowSensitiveMedia;

    @Override
    protected TimelineCases timelineCases() {
        return timelineCases;
    }

    // Each element is either a Notification for loading data or a Placeholder
    private final PairedList<Either<Placeholder, Notification>, NotificationViewData> notifications
            = new PairedList<>(new Function<Either<Placeholder, Notification>, NotificationViewData>() {
        @Override
        public NotificationViewData apply(Either<Placeholder, Notification> input) {
            if (input.isRight()) {
                Notification notification = input.asRight();
                return ViewDataUtils.notificationToViewData(
                        notification,
                        alwaysShowSensitiveMedia
                );
            } else {
                return new NotificationViewData.Placeholder(false);
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
        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        @NonNull Context context = inflater.getContext(); // from inflater to silence warning
        // Setup the SwipeRefreshLayout.
        swipeRefreshLayout = rootView.findViewById(R.id.swipeRefreshLayout);
        recyclerView = rootView.findViewById(R.id.recyclerView);
        progressBar = rootView.findViewById(R.id.progressBar);
        statusView = rootView.findViewById(R.id.statusView);

        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(context, android.R.attr.colorBackground));
        // Setup the RecyclerView.
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);

        adapter = new NotificationsAdapter(this, this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        alwaysShowSensitiveMedia = accountManager.getActiveAccount().getAlwaysShowSensitiveMedia();
        boolean mediaPreviewEnabled = accountManager.getActiveAccount().getMediaPreviewEnabled();
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        boolean useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false);
        adapter.setUseAbsoluteTime(useAbsoluteTime);
        recyclerView.setAdapter(adapter);

        notifications.clear();
        topLoading = false;
        bottomLoading = false;
        bottomId = null;

        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        sendFetchNotificationsRequest(null, null, FetchEnd.BOTTOM, -1);

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(refreshReceiver,
                new IntentFilter("refresh"));

        return rootView;
    }

    private void handleFavEvent(FavoriteEvent event) {
        Pair<Integer, Notification> posAndNotification =
                findReplyPosition(event.getStatusId());
        if (posAndNotification == null) return;
        //noinspection ConstantConditions
        setFavovouriteForStatus(posAndNotification.first,
                posAndNotification.second.getStatus(),
                event.getFavourite());
    }

    private void handleReblogEvent(ReblogEvent event) {
        Pair<Integer, Notification> posAndNotification = findReplyPosition(event.getStatusId());
        if (posAndNotification == null) return;
        //noinspection ConstantConditions
        setReblogForStatus(posAndNotification.first,
                posAndNotification.second.getStatus(),
                event.getReblog());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) throw new AssertionError("Activity is null");

        // MainActivity's layout is guaranteed to be inflated until onCreate returns.
        TabLayout layout = activity.findViewById(R.id.tab_layout);
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

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then.
         * Use a modified scroll listener that both loads more notificationsEnabled as it goes, and hides
         * the compose button on down-scroll. */
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
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
                NotificationsFragment.this.onLoadMore();
            }
        };

        recyclerView.addOnScrollListener(scrollListener);

        eventHub.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(event -> {
                    if (event instanceof FavoriteEvent) {
                        handleFavEvent((FavoriteEvent) event);
                    } else if (event instanceof ReblogEvent) {
                        handleReblogEvent((ReblogEvent) event);
                    } else if (event instanceof BlockEvent) {
                        removeAllByAccountId(((BlockEvent) event).getAccountId());
                    } else if (event instanceof PreferenceChangedEvent) {
                        onPreferenceChanged(((PreferenceChangedEvent) event).getPreferenceKey());
                    }
                });
    }

    @Override
    public void onDestroyView() {
        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null");
        } else {
            TabLayout tabLayout = activity.findViewById(R.id.tab_layout);
            tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        }

        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        swipeRefreshLayout.setEnabled(true);
        this.statusView.setVisibility(View.GONE);
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
        timelineCases.reblog(status, reblog)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this)))
                .subscribe(
                        (newStatus) -> setReblogForStatus(position, status, reblog),
                        (t) -> Log.d(getClass().getSimpleName(),
                                "Failed to reblog status: " + status.getId(), t)
                );
    }

    private void setReblogForStatus(int position, Status status, boolean reblog) {
        status.setReblogged(reblog);

        if (status.getReblog() != null) {
            status.getReblog().setReblogged(reblog);
        }

        NotificationViewData.Concrete viewdata = (NotificationViewData.Concrete) notifications.getPairedItem(position);

        StatusViewData.Builder viewDataBuilder = new StatusViewData.Builder(viewdata.getStatusViewData());
        viewDataBuilder.setReblogged(reblog);

        NotificationViewData.Concrete newViewData = new NotificationViewData.Concrete(
                viewdata.getType(), viewdata.getId(), viewdata.getAccount(),
                viewDataBuilder.createStatusViewData(), viewdata.isExpanded());

        notifications.setPairedItem(position, newViewData);

        adapter.updateItemWithNotify(position, newViewData, true);
    }


    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Notification notification = notifications.get(position).asRight();
        final Status status = notification.getStatus();

        timelineCases.favourite(status, favourite)
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this)))
                .subscribe(
                        (newStatus) -> setFavovouriteForStatus(position, status, favourite),
                        (t) -> Log.d(getClass().getSimpleName(),
                                "Failed to favourite status: " + status.getId(), t)
                );
    }

    private void setFavovouriteForStatus(int position, Status status, boolean favourite) {
        status.setFavourited(favourite);

        if (status.getReblog() != null) {
            status.getReblog().setFavourited(favourite);
        }

        NotificationViewData.Concrete viewdata = (NotificationViewData.Concrete) notifications.getPairedItem(position);

        StatusViewData.Builder viewDataBuilder = new StatusViewData.Builder(viewdata.getStatusViewData());
        viewDataBuilder.setFavourited(favourite);

        NotificationViewData.Concrete newViewData = new NotificationViewData.Concrete(
                viewdata.getType(), viewdata.getId(), viewdata.getAccount(),
                viewDataBuilder.createStatusViewData(), viewdata.isExpanded());

        notifications.setPairedItem(position, newViewData);

        adapter.updateItemWithNotify(position, newViewData, true);
    }

    @Override
    public void onMore(@NonNull View view, int position) {
        Notification notification = notifications.get(position).asRight();
        super.more(notification.getStatus(), view, position);
    }

    @Override
    public void onViewMedia(int position, int attachmentIndex, @NonNull View view) {
        Notification notification = notifications.get(position).asRightOrNull();
        if (notification == null || notification.getStatus() == null) return;
        super.viewMedia(attachmentIndex, notification.getStatus(), view);
    }

    @Override
    public void onViewThread(int position) {
        Notification notification = notifications.get(position).asRight();
        super.viewThread(notification.getStatus());
    }

    @Override
    public void onOpenReblog(int position) {
        Notification notification = notifications.get(position).asRight();
        onViewAccount(notification.getAccount().getId());
    }

    @Override
    public void onExpandedChange(boolean expanded, int position) {
        NotificationViewData.Concrete old =
                (NotificationViewData.Concrete) notifications.getPairedItem(position);
        StatusViewData.Concrete statusViewData =
                new StatusViewData.Builder(old.getStatusViewData())
                        .setIsExpanded(expanded)
                        .createStatusViewData();
        NotificationViewData notificationViewData = new NotificationViewData.Concrete(old.getType(),
                old.getId(), old.getAccount(), statusViewData, expanded);
        notifications.setPairedItem(position, notificationViewData);
        adapter.updateItemWithNotify(position, notificationViewData, false);
    }

    @Override
    public void onContentHiddenChange(boolean isShowing, int position) {
        NotificationViewData.Concrete old =
                (NotificationViewData.Concrete) notifications.getPairedItem(position);
        StatusViewData.Concrete statusViewData =
                new StatusViewData.Builder(old.getStatusViewData())
                        .setIsShowingSensitiveContent(isShowing)
                        .createStatusViewData();
        NotificationViewData notificationViewData = new NotificationViewData.Concrete(old.getType(),
                old.getId(), old.getAccount(), statusViewData, old.isExpanded());
        notifications.setPairedItem(position, notificationViewData);
        adapter.updateItemWithNotify(position, notificationViewData, false);
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
            NotificationViewData notificationViewData =
                    new NotificationViewData.Placeholder(true);
            notifications.setPairedItem(position, notificationViewData);
            adapter.updateItemWithNotify(position, notificationViewData, false);
        } else {
            Log.d(TAG, "error loading more");
        }
    }

    @Override
    public void onContentCollapsedChange(boolean isCollapsed, int position) {
        if (position < 0 || position >= notifications.size()) {
            Log.e(TAG, String.format("Tried to access out of bounds status position: %d of %d", position, notifications.size() - 1));
            return;
        }

        NotificationViewData notification = notifications.getPairedItem(position);
        if (!(notification instanceof NotificationViewData.Concrete)) {
            Log.e(TAG, String.format(
                    "Expected NotificationViewData.Concrete, got %s instead at position: %d of %d",
                    notification == null ? "null" : notification.getClass().getSimpleName(),
                    position,
                    notifications.size() - 1
            ));
            return;
        }

        StatusViewData.Concrete status = ((NotificationViewData.Concrete) notification).getStatusViewData();
        StatusViewData.Concrete updatedStatus = new StatusViewData.Builder(status)
                .setCollapsed(isCollapsed)
                .createStatusViewData();

        NotificationViewData.Concrete concreteNotification = (NotificationViewData.Concrete) notification;
        NotificationViewData updatedNotification = new NotificationViewData.Concrete(
                concreteNotification.getType(),
                concreteNotification.getId(),
                concreteNotification.getAccount(),
                updatedStatus,
                concreteNotification.isExpanded()
        );
        notifications.setPairedItem(position, updatedNotification);
        adapter.updateItemWithNotify(position, updatedNotification, false);

        // Since we cannot notify to the RecyclerView right away because it may be scrolling
        // we run this when the RecyclerView is done doing measurements and other calculations.
        // To test this is not bs: try getting a notification while scrolling, without wrapping
        // notifyItemChanged in a .post() call. App will crash.
        recyclerView.post(() -> adapter.notifyItemChanged(position, notification));
    }

    @Override
    public void onNotificationContentCollapsedChange(boolean isCollapsed, int position) {
        onContentCollapsedChange(isCollapsed, position);
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
    public void onViewStatusForNotificationId(String notificationId) {
        for (Either<Placeholder, Notification> either : notifications) {
            Notification notification = either.asRightOrNull();
            if (notification != null && notification.getId().equals(notificationId)) {
                super.viewThread(notification.getStatus());
                return;
            }
        }
        Log.w(TAG, "Didn't find a notification for ID: " + notificationId);
    }

    public void onPreferenceChanged(String key) {
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
        }
    }

    @Override
    public void removeItem(int position) {
        notifications.remove(position);
        adapter.update(notifications.getPairedCopy());
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
        adapter.update(notifications.getPairedCopy());
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
                notifications.add(new Either.Left(Placeholder.getInstance()));
                NotificationViewData viewData = new NotificationViewData.Placeholder(true);
                notifications.setPairedItem(notifications.size() - 1, viewData);
                recyclerView.post(() -> adapter.addItems(Collections.singletonList(viewData)));
            }
        }

        sendFetchNotificationsRequest(bottomId, null, FetchEnd.BOTTOM, -1);
    }

    private void jumpToTop() {
        layoutManager.scrollToPosition(0);
        scrollListener.reset();
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

        Call<List<Notification>> call = mastodonApi.notifications(fromId, uptoId, LOAD_AT_ONCE);

        call.enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(@NonNull Call<List<Notification>> call,
                                   @NonNull Response<List<Notification>> response) {
                if (response.isSuccessful()) {
                    String linkHeader = response.headers().get("Link");
                    onFetchNotificationsSuccess(response.body(), linkHeader, fetchEnd, pos);
                } else {
                    onFetchNotificationsFailure(new Exception(response.message()), fetchEnd, pos);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Notification>> call, @NonNull Throwable t) {
                onFetchNotificationsFailure((Exception) t, fetchEnd, pos);
            }
        });
        callList.add(call);
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, String linkHeader,
                                             FetchEnd fetchEnd, int pos) {
        List<HttpHeaderLink> links = HttpHeaderLink.parse(linkHeader);
        switch (fetchEnd) {
            case TOP: {
                HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                String uptoId = null;
                if (previous != null) {
                    uptoId = previous.uri.getQueryParameter("since_id");
                }
                update(notifications, null);
                break;
            }
            case MIDDLE: {
                replacePlaceholderWithNotifications(notifications, pos);
                break;
            }
            case BOTTOM: {
                HttpHeaderLink next = HttpHeaderLink.findByRelationType(links, "next");
                String fromId = null;
                if (next != null) {
                    fromId = next.uri.getQueryParameter("max_id");
                }

                if (!this.notifications.isEmpty()
                        && !this.notifications.get(this.notifications.size() - 1).isRight()) {
                    this.notifications.remove(this.notifications.size() - 1);
                    adapter.removeItemAndNotify(this.notifications.size());
                }

                if (adapter.getItemCount() > 0) {
                    addItems(notifications, fromId);
                } else {
                    /* If this is the first fetch, also save the id from the "previous" link and
                     * treat this operation as a refresh so the scroll position doesn't get pushed
                     * down to the end. */
                    HttpHeaderLink previous = HttpHeaderLink.findByRelationType(links, "prev");
                    String uptoId = null;
                    if (previous != null) {
                        uptoId = previous.uri.getQueryParameter("since_id");
                    }
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

        }
        swipeRefreshLayout.setRefreshing(false);
        progressBar.setVisibility(View.GONE);
    }

    private void onFetchNotificationsFailure(Exception exception, FetchEnd fetchEnd, int position) {
        swipeRefreshLayout.setRefreshing(false);
        if (fetchEnd == FetchEnd.MIDDLE && !notifications.get(position).isRight()) {
            NotificationViewData placeholderVD =
                    new NotificationViewData.Placeholder(false);
            notifications.setPairedItem(position, placeholderVD);
            adapter.updateItemWithNotify(position, placeholderVD, true);
        } else if (this.notifications.isEmpty()) {
            this.statusView.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setEnabled(false);
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
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
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
                    liftedNew.add(new Either.Left(Placeholder.getInstance()));
                }
                notifications.addAll(0, liftedNew);
            } else {
                notifications.addAll(0, liftedNew.subList(0, newIndex));
            }
        }
        adapter.update(notifications.getPairedCopy());
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
            List<NotificationViewData> newViewDatas = notifications.getPairedCopy()
                    .subList(notifications.size() - newNotifications.size(),
                            notifications.size());
            adapter.addItems(newViewDatas);
        }
    }

    private void replacePlaceholderWithNotifications(List<Notification> newNotifications, int pos) {
        // Remove placeholder
        notifications.remove(pos);

        if (ListUtils.isEmpty(newNotifications)) {
            adapter.update(notifications.getPairedCopy());
            return;
        }

        List<Either<Placeholder, Notification>> liftedNew = liftNotificationList(newNotifications);

        // If we fetched less posts than in the limit, it means that the hole is not filled
        // If we fetched at least as much it means that there are more posts to load and we should
        // insert new placeholder
        if (newNotifications.size() >= LOAD_AT_ONCE) {
            liftedNew.add(new Either.Left(Placeholder.getInstance()));
        }

        notifications.addAll(pos, liftedNew);
        adapter.update(notifications.getPairedCopy());
    }

    private final Function<Notification, Either<Placeholder, Notification>> notificationLifter =
            Either.Right::new;

    private List<Either<Placeholder, Notification>> liftNotificationList(List<Notification> list) {
        return CollectionUtil.map(list, notificationLifter);
    }

    private void fullyRefresh() {
        adapter.clear();
        notifications.clear();
        sendFetchNotificationsRequest(null, null, FetchEnd.TOP, -1);
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

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //refresh
            onRefresh();
        }
    };
}
