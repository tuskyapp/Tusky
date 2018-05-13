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
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.PopupMenu;
import android.text.Spanned;
import android.view.View;

import com.keylesspalace.tusky.BottomSheetActivity;
import com.keylesspalace.tusky.ComposeActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ReportActivity;
import com.keylesspalace.tusky.TuskyApplication;
import com.keylesspalace.tusky.ViewMediaActivity;
import com.keylesspalace.tusky.ViewTagActivity;
import com.keylesspalace.tusky.ViewVideoActivity;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.network.TimelineCases;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.viewdata.AttachmentViewData;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/* Note from Andrew on Jan. 22, 2017: This class is a design problem for me, so I left it with an
 * awkward name. TimelineFragment and NotificationFragment have significant overlap but the nature
 * of that is complicated by how they're coupled with Status and Notification and the corresponding
 * adapters. I feel like the profile pages and thread viewer, which I haven't made yet, will also
 * overlap functionality. So, I'm momentarily leaving it and hopefully working on those will clear
 * up what needs to be where. */
public abstract class SFragment extends BaseFragment {
    protected static final int COMPOSE_RESULT = 1;

    protected String loggedInAccountId;
    protected String loggedInUsername;

    protected abstract TimelineCases timelineCases();
    protected abstract void removeItem(int position);

    private BottomSheetActivity bottomSheetActivity;

    @Inject
    public MastodonApi mastodonApi;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        AccountEntity activeAccount = TuskyApplication.getInstance(getContext()).getServiceLocator()
                .get(AccountManager.class).getActiveAccount();
        if (activeAccount != null) {
            loggedInAccountId = activeAccount.getAccountId();
            loggedInUsername = activeAccount.getUsername();
        }
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        getActivity().overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof BottomSheetActivity) {
            bottomSheetActivity = (BottomSheetActivity)context;
        } else {
            throw new IllegalStateException("Fragment must be attached to a BottomSheetActivity!");
        }
    }

    protected void openReblog(@Nullable final Status status) {
        if (status == null) return;
        bottomSheetActivity.viewAccount(status.getAccount().getId());
    }

    protected void viewThread(Status status) {
        bottomSheetActivity.viewThread(status);
    }

    protected void viewAccount(String accountId) {
        bottomSheetActivity.viewAccount(accountId);
    }

    public void onViewUrl(String url) {
        bottomSheetActivity.viewUrl(url);
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

    protected void viewTag(String tag) {
        Intent intent = new Intent(getContext(), ViewTagActivity.class);
        intent.putExtra("hashtag", tag);
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


}
