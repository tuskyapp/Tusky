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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.PopupMenu;
import android.text.Spanned;
import android.view.View;
import android.widget.LinearLayout;

import com.keylesspalace.tusky.AccountActivity;
import com.keylesspalace.tusky.ComposeActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ReportActivity;
import com.keylesspalace.tusky.TuskyApplication;
import com.keylesspalace.tusky.ViewMediaActivity;
import com.keylesspalace.tusky.ViewTagActivity;
import com.keylesspalace.tusky.ViewThreadActivity;
import com.keylesspalace.tusky.ViewVideoActivity;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.SearchResults;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.AdapterItemRemover;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.network.TimelineCases;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.viewdata.AttachmentViewData;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/* Note from Andrew on Jan. 22, 2017: This class is a design problem for me, so I left it with an
 * awkward name. TimelineFragment and NotificationFragment have significant overlap but the nature
 * of that is complicated by how they're coupled with Status and Notification and the corresponding
 * adapters. I feel like the profile pages and thread viewer, which I haven't made yet, will also
 * overlap functionality. So, I'm momentarily leaving it and hopefully working on those will clear
 * up what needs to be where. */
public abstract class SFragment extends BaseFragment implements AdapterItemRemover {
    protected static final int COMPOSE_RESULT = 1;

    protected String loggedInAccountId;
    protected String loggedInUsername;
    protected String searchUrl;

    protected abstract TimelineCases timelineCases();
    protected BottomSheetBehavior bottomSheet;

    @Inject
    protected MastodonApi mastodonApi;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        AccountEntity activeAccount = TuskyApplication.getInstance(getContext()).getServiceLocator()
                .get(AccountManager.class).getActiveAccount();
        if (activeAccount != null) {
            loggedInAccountId = activeAccount.getAccountId();
            loggedInUsername = activeAccount.getUsername();
        }
        setupBottomSheet(getView());
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        getActivity().overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
    }

    protected void openReblog(@Nullable final Status status) {
        if (status == null) return;
        viewAccount(status.getAccount().getId());
    }

    protected void reply(Status status) {
        String inReplyToId = status.getActionableId();
        Status actionableStatus = status.getActionableStatus();
        Status.Visibility replyVisibility = actionableStatus.getVisibility();
        String contentWarning = actionableStatus.getSpoilerText();
        Status.Mention[] mentions = actionableStatus.getMentions();
        Set<String> mentionedUsernames = new LinkedHashSet<>();
        mentionedUsernames.add(actionableStatus.getAccount().getUsername());
        for (Status.Mention mention : mentions) {
            mentionedUsernames.add(mention.getUsername());
        }
        mentionedUsernames.remove(loggedInUsername);
        Intent intent = new ComposeActivity.IntentBuilder()
                .inReplyToId(inReplyToId)
                .replyVisibility(replyVisibility)
                .contentWarning(contentWarning)
                .mentionedUsernames(mentionedUsernames)
                .repyingStatusAuthor(actionableStatus.getAccount().getLocalUsername())
                .replyingStatusContent(actionableStatus.getContent().toString())
                .build(getContext());
        startActivityForResult(intent, COMPOSE_RESULT);
    }

    protected void more(final Status status, View view, final int position) {
        final String id = status.getActionableId();
        final String accountId = status.getActionableStatus().getAccount().getId();
        final String accountUsename = status.getActionableStatus().getAccount().getUsername();
        final Spanned content = status.getActionableStatus().getContent();
        final String statusUrl = status.getActionableStatus().getUrl();
        PopupMenu popup = new PopupMenu(getContext(), view);
        // Give a different menu depending on whether this is the user's own toot or not.
        if (loggedInAccountId == null || !loggedInAccountId.equals(accountId)) {
            popup.inflate(R.menu.status_more);
        } else {
            popup.inflate(R.menu.status_more_for_user);
        }
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.status_share_content: {
                    StringBuilder sb = new StringBuilder();
                    sb.append(status.getAccount().getUsername());
                    sb.append(" - ");
                    sb.append(status.getContent().toString());

                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_status_content_to)));
                    return true;
                }
                case R.id.status_share_link: {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, statusUrl);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_status_link_to)));
                    return true;
                }
                case R.id.status_copy_link: {
                    ClipboardManager clipboard = (ClipboardManager)
                            getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(null, statusUrl);
                    clipboard.setPrimaryClip(clip);
                    return true;
                }
                case R.id.status_mute: {
                    timelineCases().mute(accountId);
                    return true;
                }
                case R.id.status_block: {
                    timelineCases().block(accountId);
                    return true;
                }
                case R.id.status_report: {
                    openReportPage(accountId, accountUsename, id, content);
                    return true;
                }
                case R.id.status_delete: {
                    timelineCases().delete(id);
                    removeItem(position);
                    return true;
                }
            }
            return false;
        });
        popup.show();
    }

    protected void viewMedia(int urlIndex, Status status, @Nullable View view) {
        final Status actionable = status.getActionableStatus();
        final Attachment active = actionable.getAttachments().get(urlIndex);
        Attachment.Type type = active.getType();
        switch (type) {
            case IMAGE: {
                final List<AttachmentViewData> attachments = AttachmentViewData.list(actionable);
                final Intent intent = ViewMediaActivity.newIntent(getContext(), attachments,
                        urlIndex);
                if (view != null) {
                    String url = active.getUrl();
                    ViewCompat.setTransitionName(view, url);
                    ActivityOptionsCompat options =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                                    view, url);
                    startActivity(intent, options.toBundle());
                } else {
                    startActivity(intent);
                }
                break;
            }
            case GIFV:
            case VIDEO: {
                Intent intent = new Intent(getContext(), ViewVideoActivity.class);
                intent.putExtra("url", active.getUrl());
                startActivity(intent);
                break;
            }
            case UNKNOWN: {
                /* Intentionally do nothing. This case is here is to handle when new attachment
                 * types are added to the API before code is added here to handle them. So, the
                 * best fallback is to just show the preview and ignore requests to view them. */
                break;
            }
        }
    }

    protected void viewThread(Status status) {
        if (!isSearching()) {
            startActivity(ViewThreadActivity.startIntentFromStatus(getContext(), status));
        }
    }

    protected void viewTag(String tag) {
        Intent intent = new Intent(getContext(), ViewTagActivity.class);
        intent.putExtra("hashtag", tag);
        startActivity(intent);
    }

    protected void viewAccount(String id) {
        Intent intent = new Intent(getContext(), AccountActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    protected void openReportPage(String accountId, String accountUsername, String statusId,
                                  Spanned statusContent) {
        Intent intent = new Intent(getContext(), ReportActivity.class);
        intent.putExtra("account_id", accountId);
        intent.putExtra("account_username", accountUsername);
        intent.putExtra("status_id", statusId);
        intent.putExtra("status_content", HtmlUtils.toHtml(statusContent));
        startActivity(intent);
    }

    // https://mastodon.foo.bar/@User
    // https://mastodon.foo.bar/@User/43456787654678
    // https://pleroma.foo.bar/users/User
    // https://pleroma.foo.bar/users/43456787654678
    // https://pleroma.foo.bar/notice/43456787654678
    // https://pleroma.foo.bar/objects/d4643c42-3ae0-4b73-b8b0-c725f5819207
    static boolean looksLikeMastodonUrl(String urlString) {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            return false;
        }

        if (uri.getQuery() != null ||
                uri.getFragment() != null ||
                uri.getPath() == null) {
            return false;
        }

        String path = uri.getPath();
        return path.matches("^/@[^/]+$") ||
                path.matches("^/users/[^/]+$") ||
                path.matches("^/@[^/]+/\\d+$") ||
                path.matches("^/notice/\\d+$") ||
                path.matches("^/objects/[-a-f0-9]+$");
    }

    void onBeginSearch(@NonNull String url) {
        searchUrl = url;
        showQuerySheet();
    }

    boolean getCancelSearchRequested(@NonNull String url) {
        return !url.equals(searchUrl);
    }

    boolean isSearching() {
        return searchUrl != null;
    }

    void onEndSearch(@NonNull String url) {
        if (url.equals(searchUrl)) {
            // Don't clear query if there's no match,
            // since we might just now be getting the response for a canceled search
            searchUrl = null;
            hideQuerySheet();
        }
    }

    void cancelActiveSearch()
    {
        if (isSearching()) {
            onEndSearch(searchUrl);
        }
    }

    void openLink(@NonNull String url) {
        LinkHelper.openLink(url, getContext());
    }

    public void onViewURL(String url) {
        if (!looksLikeMastodonUrl(url)) {
            openLink(url);
            return;
        }

        Call<SearchResults> call = mastodonApi.search(url, true);
        call.enqueue(new Callback<SearchResults>() {
            @Override
            public void onResponse(@NonNull Call<SearchResults> call, @NonNull Response<SearchResults> response) {
                if (getCancelSearchRequested(url)) {
                    return;
                }

                onEndSearch(url);
                if (response.isSuccessful()) {
                    // According to the mastodon API doc, if the search query is a url,
                    // only exact matches for statuses or accounts are returned
                    // which is good, because pleroma returns a different url
                    // than the public post link
                    List<Status> statuses = response.body().getStatuses();
                    List<Account> accounts = response.body().getAccounts();
                    if (statuses != null && !statuses.isEmpty()) {
                        viewThread(statuses.get(0));
                        return;
                    } else if (accounts != null && !accounts.isEmpty()) {
                        viewAccount(accounts.get(0).getId());
                        return;
                    }
                }
                openLink(url);
            }

            @Override
            public void onFailure(@NonNull Call<SearchResults> call, @NonNull Throwable t) {
                if (!getCancelSearchRequested(url)) {
                    onEndSearch(url);
                    openLink(url);
                }
            }
        });
        callList.add(call);
        onBeginSearch(url);
    }

    protected void setupBottomSheet(View view)
    {
        LinearLayout bottomSheetLayout = view.findViewById(R.id.item_status_bottom_sheet);
        if (bottomSheetLayout != null) {
            bottomSheet = BottomSheetBehavior.from(bottomSheetLayout);
            bottomSheet.setState(BottomSheetBehavior.STATE_HIDDEN);
            bottomSheet.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    switch(newState) {
                        case BottomSheetBehavior.STATE_HIDDEN:
                            cancelActiveSearch();
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                }
            });
        }
    }

    private void showQuerySheet() {
        if (bottomSheet != null)
            bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void hideQuerySheet() {
        if (bottomSheet != null)
            bottomSheet.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
}
