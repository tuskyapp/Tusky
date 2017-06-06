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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.view.MenuItem;
import android.view.View;

import com.keylesspalace.tusky.AccountActivity;
import com.keylesspalace.tusky.BaseActivity;
import com.keylesspalace.tusky.ComposeActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ReportActivity;
import com.keylesspalace.tusky.ViewTagActivity;
import com.keylesspalace.tusky.ViewThreadActivity;
import com.keylesspalace.tusky.ViewVideoActivity;
import com.keylesspalace.tusky.entity.Relationship;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.AdapterItemRemover;
import com.keylesspalace.tusky.network.MastodonAPI;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/* Note from Andrew on Jan. 22, 2017: This class is a design problem for me, so I left it with an
 * awkward name. TimelineFragment and NotificationFragment have significant overlap but the nature
 * of that is complicated by how they're coupled with Status and Notification and the corresponding
 * adapters. I feel like the profile pages and thread viewer, which I haven't made yet, will also
 * overlap functionality. So, I'm momentarily leaving it and hopefully working on those will clear
 * up what needs to be where. */
public abstract class SFragment extends BaseFragment {
    protected String loggedInAccountId;
    protected String loggedInUsername;
    protected MastodonAPI mastodonAPI;
    protected static int COMPOSE_RESULT = 1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getPrivatePreferences();
        loggedInAccountId = preferences.getString("loggedInAccountId", null);
        loggedInUsername = preferences.getString("loggedInAccountUsername", null);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        BaseActivity activity = (BaseActivity) getActivity();
        mastodonAPI = activity.mastodonAPI;
    }

    protected void reply(Status status) {
        String inReplyToId = status.getActionableId();
        Status actionableStatus = status.getActionableStatus();
        String replyVisibility = actionableStatus.getVisibility().toString().toLowerCase();
        String contentWarning = actionableStatus.spoilerText;
        Status.Mention[] mentions = actionableStatus.mentions;
        List<String> mentionedUsernames = new ArrayList<>();
        for (Status.Mention mention : mentions) {
            mentionedUsernames.add(mention.username);
        }
        mentionedUsernames.add(actionableStatus.account.username);
        mentionedUsernames.remove(loggedInUsername);
        Intent intent = new Intent(getContext(), ComposeActivity.class);
        intent.putExtra("in_reply_to_id", inReplyToId);
        intent.putExtra("reply_visibility", replyVisibility);
        intent.putExtra("content_warning", contentWarning);
        intent.putExtra("mentioned_usernames", mentionedUsernames.toArray(new String[0]));
        startActivityForResult(intent, COMPOSE_RESULT);
    }

    protected void reblog(final Status status, final boolean reblog,
                          final RecyclerView.Adapter adapter, final int position) {
        String id = status.getActionableId();

        Callback<Status> cb = new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    status.reblogged = reblog;

                    if (status.reblog != null) {
                        status.reblog.reblogged = reblog;
                    }

                    adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {}
        };

        Call<Status> call;
        if (reblog) {
            call = mastodonAPI.reblogStatus(id);
        } else {
            call = mastodonAPI.unreblogStatus(id);
        }
        call.enqueue(cb);
        callList.add(call);
    }

    protected void favourite(final Status status, final boolean favourite,
            final RecyclerView.Adapter adapter, final int position) {
        String id = status.getActionableId();

        Callback<Status> cb = new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, retrofit2.Response<Status> response) {
                if (response.isSuccessful()) {
                    status.favourited = favourite;

                    if (status.reblog != null) {
                        status.reblog.favourited = favourite;
                    }

                    adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {}
        };

        Call<Status> call;
        if (favourite) {
            call = mastodonAPI.favouriteStatus(id);
        } else {
            call = mastodonAPI.unfavouriteStatus(id);
        }
        call.enqueue(cb);
        callList.add(call);
    }

    private void mute(String id) {
        Call<Relationship> call = mastodonAPI.muteAccount(id);
        call.enqueue(new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {}

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {}
        });
        callList.add(call);
        Intent intent = new Intent(TimelineReceiver.Types.MUTE_ACCOUNT);
        intent.putExtra("id", id);
        LocalBroadcastManager.getInstance(getContext())
                .sendBroadcast(intent);
    }

    private void block(String id) {
        Call<Relationship> call = mastodonAPI.blockAccount(id);
        call.enqueue(new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, retrofit2.Response<Relationship> response) {}

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {}
        });
        callList.add(call);
        Intent intent = new Intent(TimelineReceiver.Types.BLOCK_ACCOUNT);
        intent.putExtra("id", id);
        LocalBroadcastManager.getInstance(getContext())
                .sendBroadcast(intent);
    }

    private void delete(String id) {
        Call<ResponseBody> call = mastodonAPI.deleteStatus(id);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {}

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
        callList.add(call);
    }

    protected void more(final Status status, View view, final AdapterItemRemover adapter,
                        final int position) {
        final String id = status.getActionableId();
        final String accountId = status.getActionableStatus().account.id;
        final String accountUsename = status.getActionableStatus().account.username;
        final Spanned content = status.getActionableStatus().content;
        final String statusUrl = status.getActionableStatus().url;
        PopupMenu popup = new PopupMenu(getContext(), view);
        // Give a different menu depending on whether this is the user's own toot or not.
        if (loggedInAccountId == null || !loggedInAccountId.equals(accountId)) {
            popup.inflate(R.menu.status_more);
        } else {
            popup.inflate(R.menu.status_more_for_user);
        }
        popup.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.status_share_content: {
                                StringBuilder sb = new StringBuilder();
                                sb.append(status.account.username);
                                sb.append(" - ");
                                sb.append(status.content.toString());

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
                            case R.id.status_mute: {
                                mute(accountId);
                                return true;
                            }
                            case R.id.status_block: {
                                block(accountId);
                                return true;
                            }
                            case R.id.status_report: {
                                openReportPage(accountId, accountUsename, id, content);
                                return true;
                            }
                            case R.id.status_delete: {
                                delete(id);
                                adapter.removeItem(position);
                                return true;
                            }
                        }
                        return false;
                    }
                });
        popup.show();
    }

    protected void viewMedia(String url, Status.MediaAttachment.Type type) {
        switch (type) {
            case IMAGE: {
                DialogFragment newFragment = ViewMediaFragment.newInstance(url);
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                newFragment.show(ft, "view_media");
                break;
            }
            case GIFV:
            case VIDEO: {
                Intent intent = new Intent(getContext(), ViewVideoActivity.class);
                intent.putExtra("url", url);
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
        Intent intent = new Intent(getContext(), ViewThreadActivity.class);
        intent.putExtra("id", status.getActionableId());
        startActivity(intent);
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

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        getActivity().overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
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
