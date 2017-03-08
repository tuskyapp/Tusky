/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.view.MenuItem;
import android.view.View;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.keylesspalace.tusky.entity.Status;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;

/* Note from Andrew on Jan. 22, 2017: This class is a design problem for me, so I left it with an
 * awkward name. TimelineFragment and NotificationFragment have significant overlap but the nature
 * of that is complicated by how they're coupled with Status and Notification and the corresponding
 * adapters. I feel like the profile pages and thread viewer, which I haven't made yet, will also
 * overlap functionality. So, I'm momentarily leaving it and hopefully working on those will clear
 * up what needs to be where. */
public class SFragment extends Fragment {
    private static final String TAG = "SFragment"; // logging tag and Volley request tag

    protected String domain;
    protected String accessToken;
    protected String loggedInAccountId;
    protected String loggedInUsername;
    private MastodonAPI api;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        loggedInAccountId = preferences.getString("loggedInAccountId", null);
        loggedInUsername = preferences.getString("loggedInAccountUsername", null);
        api = ((BaseActivity) getActivity()).mastodonAPI;
    }

    @Override
    public void onDestroy() {
        VolleySingleton.getInstance(getContext()).cancelAll(TAG);
        super.onDestroy();
    }

    protected void reply(Status status) {
        String inReplyToId = status.getActionableId();
        Status.Mention[] mentions = status.mentions;
        List<String> mentionedUsernames = new ArrayList<>();
        for (Status.Mention mention : mentions) {
            mentionedUsernames.add(mention.username);
        }
        mentionedUsernames.add(status.account.username);
        mentionedUsernames.remove(loggedInUsername);
        Intent intent = new Intent(getContext(), ComposeActivity.class);
        intent.putExtra("in_reply_to_id", inReplyToId);
        intent.putExtra("reply_visibility", status.visibility.toString().toLowerCase());
        intent.putExtra("mentioned_usernames", mentionedUsernames.toArray(new String[0]));
        startActivity(intent);
    }

    protected void reblog(final Status status, final boolean reblog,
            final RecyclerView.Adapter adapter, final int position) {
        String id = status.getActionableId();

        Callback<Status> cb = new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, retrofit2.Response<Status> response) {
                status.reblogged = reblog;
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {

            }
        };

        if (reblog) {
            api.reblogStatus(id).enqueue(cb);
        } else {
            api.unreblogStatus(id).enqueue(cb);
        }
    }

    protected void favourite(final Status status, final boolean favourite,
            final RecyclerView.Adapter adapter, final int position) {
        String id = status.getActionableId();

        Callback<Status> cb = new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, retrofit2.Response<Status> response) {
                status.favourited = favourite;
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {

            }
        };

        if (favourite) {
            api.favouriteStatus(id).enqueue(cb);
        } else {
            api.unfavouriteStatus(id).enqueue(cb);
        }
    }

    protected void follow(String id) {
        api.followAccount(id).enqueue(null);
    }

    private void block(String id) {
        api.blockAccount(id).enqueue(null);
    }

    private void delete(String id) {
        api.deleteStatus(id).enqueue(null);
    }

    protected void more(Status status, View view, final AdapterItemRemover adapter,
            final int position) {
        final String id = status.getActionableId();
        final String accountId = status.getActionableStatus().account.id;
        final String accountUsename = status.getActionableStatus().account.username;
        final Spanned content = status.getActionableStatus().content;
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
                            case R.id.status_follow: {
                                follow(accountId);
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

    private boolean fileExtensionMatches(String url, String extension) {
        extension = "." + extension;
        int parametersStart = url.indexOf('?');
        if (parametersStart == -1) {
            return url.toLowerCase().endsWith(extension);
        } else {
            int start = parametersStart - extension.length();
            return start > 0 && url.substring(start, parametersStart).equals(extension);
        }
    }

    protected void viewMedia(String url, Status.MediaAttachment.Type type) {
        switch (type) {
            case IMAGE: {
                Fragment newFragment = ViewMediaFragment.newInstance(url);

                FragmentManager manager = getFragmentManager();
                manager.beginTransaction()
                        .add(R.id.overlay_fragment_container, newFragment)
                        .addToBackStack(null)
                        .commit();
                break;
            }
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
        intent.putExtra("id", status.id);
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
