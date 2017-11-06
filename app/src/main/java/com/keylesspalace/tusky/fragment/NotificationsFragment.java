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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.MainActivity;
import com.keylesspalace.tusky.adapter.FooterViewHolder;
import com.keylesspalace.tusky.adapter.NotificationsAdapter;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.CollectionUtil;
import com.keylesspalace.tusky.util.Either;
import com.keylesspalace.tusky.util.HttpHeaderLink;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.PairedList;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.ViewDataUtils;
import com.keylesspalace.tusky.view.EndlessOnScrollListener;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.Iterator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsFragment extends SFragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener,
        NotificationsAdapter.NotificationActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NotificationF"; // logging tag

    private static final int LOAD_AT_ONCE = 30;

    private enum FetchEnd {
        TOP,
        BOTTOM,
        MIDDLE
    }

    /**
     * Placeholder for the notifications. Consider moving to the separate class to hide constructor
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

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager layoutManager;
    private RecyclerView recyclerView;
    private EndlessOnScrollListener scrollListener;
    private NotificationsAdapter adapter;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;
    private boolean hideFab;
    private TimelineReceiver timelineReceiver;
    private boolean topLoading;
    private int topFetches;
    private boolean bottomLoading;
    private int bottomFetches;
    private String bottomId;
    private String topId;

    // Each element is either a Notification for loading data or a Placeholder
    private final PairedList<Either<Placeholder, Notification>, NotificationViewData> notifications
            = new PairedList<>(new Function<Either<Placeholder, Notification>, NotificationViewData>() {
        @Override
        public NotificationViewData apply(Either<Placeholder, Notification> input) {
            if (input.isRight()) {
                Notification notification = input.getAsRight();
                return ViewDataUtils.notificationToViewData(notification);
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
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        // Setup the SwipeRefreshLayout.
        Context context = getContext();
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Setup the RecyclerView.
        recyclerView = rootView.findViewById(R.id.recycler_view);
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
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());
        boolean mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true);
        adapter.setMediaPreviewEnabled(mediaPreviewEnabled);
        recyclerView.setAdapter(adapter);

        timelineReceiver = new TimelineReceiver(this);
        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .registerReceiver(timelineReceiver, TimelineReceiver.getFilter(null));

        notifications.clear();
        topLoading = false;
        topFetches = 0;
        bottomLoading = false;
        bottomFetches = 0;
        bottomId = null;
        topId = null;

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        MainActivity activity = (MainActivity) getActivity();

        // MainActivity's layout is guaranteed to be inflated until onCreate returns.
        TabLayout layout = activity.findViewById(R.id.tab_layout);
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

        /* This is delayed until onActivityCreated solely because MainActivity.composeButton isn't
         * guaranteed to be set until then.
         * Use a modified scroll listener that both loads more notifications as it goes, and hides
         * the compose button on down-scroll. */
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.registerOnSharedPreferenceChangeListener(this);
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
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                NotificationsFragment.this.onLoadMore();
            }
        };

        recyclerView.addOnScrollListener(scrollListener);
    }

    @Override
    public void onDestroyView() {
        TabLayout tabLayout = getActivity().findViewById(R.id.tab_layout);
        tabLayout.removeOnTabSelectedListener(onTabSelectedListener);

        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(timelineReceiver);

        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        sendFetchNotificationsRequest(null, topId, FetchEnd.TOP, -1);
    }

    @Override
    public void onReply(int position) {
        super.reply(notifications.get(position).getAsRight().status);
    }

    @Override
    public void onReblog(final boolean reblog, final int position) {
        final Notification notification = notifications.get(position).getAsRight();
        final Status status = notification.status;
        reblogWithCallback(status, reblog, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    status.reblogged = reblog;

                    if (status.reblog != null) {
                        status.reblog.reblogged = reblog;
                    }
                    // Java's type inference *eyeroll*
                    notifications.set(position,
                            Either.<Placeholder, Notification>right(notification));

                    adapter.updateItemWithNotify(position, notifications.getPairedItem(position), true);

                    adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(getClass().getSimpleName(), "Failed to reblog status: " + status.id, t);
            }
        });
    }


    @Override
    public void onFavourite(final boolean favourite, final int position) {
        final Notification notification = notifications.get(position).getAsRight();
        final Status status = notification.status;
        favouriteWithCallback(status, favourite, new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    status.favourited = favourite;

                    if (status.reblog != null) {
                        status.reblog.favourited = favourite;
                    }

                    notifications.set(position,
                            Either.<Placeholder, Notification>right(notification));

                    adapter.updateItemWithNotify(position, notifications.getPairedItem(position), true);

                    adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                Log.d(getClass().getSimpleName(), "Failed to favourite status: " + status.id, t);
            }
        });
    }

    @Override
    public void onMore(View view, int position) {
        Notification notification = notifications.get(position).getAsRight();
        super.more(notification.status, view, position);
    }

    @Override
    public void onViewMedia(String[] urls, int urlIndex, Status.MediaAttachment.Type type,
                            View view) {
        super.viewMedia(urls, urlIndex, type, view);
    }

    @Override
    public void onViewThread(int position) {
        Notification notification = notifications.get(position).getAsRight();
        super.viewThread(notification.status);
    }

    @Override
    public void onOpenReblog(int position) {
        Notification notification = notifications.get(position).getAsRight();
        onViewAccount(notification.account.id);
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
                old.getId(), old.getAccount(), statusViewData);
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
                old.getId(), old.getAccount(), statusViewData);
        notifications.setPairedItem(position, notificationViewData);
        adapter.updateItemWithNotify(position, notificationViewData, false);
    }

    @Override
    public void onLoadMore(int position) {
        //check bounds before accessing list,
        if (notifications.size() >= position && position > 0) {
            Notification previous = notifications.get(position - 1).getAsRightOrNull();
            Notification next = notifications.get(position + 1).getAsRightOrNull();
            if (previous == null || next == null) {
                Log.e(TAG, "Failed to load more, invalid placeholder position: " + position);
                return;
            }
            sendFetchNotificationsRequest(previous.id, next.id, FetchEnd.MIDDLE, position);
            NotificationViewData notificationViewData =
                    new NotificationViewData.Placeholder(true);
            notifications.setPairedItem(position, notificationViewData);
            adapter.updateItemWithNotify(position, notificationViewData, false);
        } else {
            Log.d(TAG, "error loading more");
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "fabHide": {
                hideFab = sharedPreferences.getBoolean("fabHide", false);
                break;
            }
            case "mediaPreviewEnabled": {
                boolean enabled = sharedPreferences.getBoolean("mediaPreviewEnabled", true);
                adapter.setMediaPreviewEnabled(enabled);
                fullyRefresh();
                break;
            }
        }
    }

    @Override
    public void removeItem(int position) {
        notifications.remove(position);
        adapter.update(notifications.getPairedCopy());
    }

    @Override
    public void removeAllByAccountId(String accountId) {
        // using iterator to safely remove items while iterating
        Iterator<Either<Placeholder, Notification>> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Either<Placeholder, Notification> notification = iterator.next();
            Notification maybeNotification = notification.getAsRightOrNull();
            if (maybeNotification != null && maybeNotification.account.id.equals(accountId)) {
                iterator.remove();
            }
        }
        adapter.update(notifications.getPairedCopy());
    }

    private void onLoadMore() {
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
            topFetches++;
            return;
        }
        if (fetchEnd == FetchEnd.BOTTOM && bottomLoading) {
            bottomFetches++;
            return;
        }

        if (fromId != null || adapter.getItemCount() <= 1) {
            /* When this is called by the EndlessScrollListener it cannot refresh the footer state
             * using adapter.notifyItemChanged. So its necessary to postpone doing so until a
             * convenient time for the UI thread using a Runnable. */
            recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    adapter.setFooterState(FooterViewHolder.State.LOADING);
                }
            });
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
                update(notifications, null, uptoId);
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
                if (adapter.getItemCount() > 1) {
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
                    update(notifications, fromId, uptoId);
                }
                /* Set last update id for pull notifications so that we don't get notified
                 * about things we already loaded here */
                getPrivatePreferences().edit()
                        .putString("lastUpdateId", fromId)
                        .apply();
                break;
            }
        }
        fulfillAnyQueuedFetches(fetchEnd);
        if (notifications.size() == 0 && adapter.getItemCount() == 1) {
            adapter.setFooterState(FooterViewHolder.State.EMPTY);
        } else {
            adapter.setFooterState(FooterViewHolder.State.END);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void update(@Nullable List<Notification> newNotifications, @Nullable String fromId,
                        @Nullable String uptoId) {
        if (ListUtils.isEmpty(newNotifications)) {
            return;
        }
        if (fromId != null) {
            bottomId = fromId;
        }
        if (uptoId != null) {
            topId = uptoId;
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
                    liftedNew.add(Either.<Placeholder, Notification>left(Placeholder.getInstance()));
                }
                notifications.addAll(0, liftedNew);
            } else {
                notifications.addAll(0, liftedNew.subList(0, newIndex));
            }
        }
        adapter.update(notifications.getPairedCopy());
    }

    private void addItems(List<Notification> newNotifications, @Nullable String fromId) {
        if (ListUtils.isEmpty(newNotifications)) {
            return;
        }
        if (fromId != null) {
            bottomId = fromId;
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


    private void onFetchNotificationsFailure(Exception exception, FetchEnd fetchEnd, int position) {
        swipeRefreshLayout.setRefreshing(false);
        if (fetchEnd == FetchEnd.MIDDLE && !notifications.get(position).isRight()) {
            NotificationViewData placeholderVD =
                    new NotificationViewData.Placeholder(false);
            notifications.setPairedItem(position, placeholderVD);
            adapter.updateItemWithNotify(position, placeholderVD, true);
        }
        Log.e(TAG, "Fetch failure: " + exception.getMessage());
        fulfillAnyQueuedFetches(fetchEnd);
    }

    private void fulfillAnyQueuedFetches(FetchEnd fetchEnd) {
        switch (fetchEnd) {
            case BOTTOM: {
                bottomLoading = false;
                if (bottomFetches > 0) {
                    bottomFetches--;
                    onLoadMore();
                }
                break;
            }
            case TOP: {
                topLoading = false;
                if (topFetches > 0) {
                    topFetches--;
                    onRefresh();
                }
                break;
            }
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
            liftedNew.add(Either.<Placeholder, Notification>left(Placeholder.getInstance()));
        }

        notifications.addAll(pos, liftedNew);
        adapter.update(notifications.getPairedCopy());
    }

    private final Function<Notification, Either<Placeholder, Notification>> notificationLifter =
            new Function<Notification, Either<Placeholder, Notification>>() {
                @Override
                public Either<Placeholder, Notification> apply(Notification input) {
                    return Either.right(input);
                }
            };

    private List<Either<Placeholder, Notification>> liftNotificationList(List<Notification> list) {
        return CollectionUtil.map(list, notificationLifter);
    }

    private void fullyRefresh() {
        adapter.clear();
        notifications.clear();
        sendFetchNotificationsRequest(null, null, FetchEnd.TOP, -1);
    }
}
