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
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AccountActivity extends AppCompatActivity {
    private static final String TAG = "AccountActivity"; // logging tag

    private String domain;
    private String accessToken;
    private String accountId;
    private boolean following = false;
    private boolean blocking = false;
    private boolean isSelf = false;
    private String openInWebUrl;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        Intent intent = getIntent();
        accountId = intent.getStringExtra("id");

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        String loggedInAccountId = preferences.getString("loggedInAccountId", null);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NetworkImageView avatar = (NetworkImageView) findViewById(R.id.account_avatar);
        NetworkImageView header = (NetworkImageView) findViewById(R.id.account_header);
        avatar.setDefaultImageResId(R.drawable.avatar_default);
        avatar.setErrorImageResId(R.drawable.avatar_error);
        header.setDefaultImageResId(R.drawable.account_header_default);

        obtainAccount();
        if (!accountId.equals(loggedInAccountId)) {
            obtainRelationships();
        } else {
            /* Cause the options menu to update and instead show an options menu for when the
             * account being shown is their own account. */
            isSelf = true;
            invalidateOptionsMenu();
        }

        // Setup the tabs and timeline pager.
        AccountPagerAdapter adapter = new AccountPagerAdapter(
                getSupportFragmentManager(), this, accountId);
        String[] pageTitles = {
            getString(R.string.title_statuses),
            getString(R.string.title_follows),
            getString(R.string.title_followers)
        };
        adapter.setPageTitles(pageTitles);
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(adapter);
        tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            tab.setCustomView(adapter.getTabView(i));
        }
    }

    private void obtainAccount() {
        String endpoint = String.format(getString(R.string.endpoint_accounts), accountId);
        String url = "https://" + domain + endpoint;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Account account;
                        try {
                            account = Account.parse(response);
                        } catch (JSONException e) {
                            onObtainAccountFailure();
                            return;
                        }
                        onObtainAccountSuccess(account);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onObtainAccountFailure();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onObtainAccountSuccess(Account account) {
        TextView username = (TextView) findViewById(R.id.account_username);
        TextView displayName = (TextView) findViewById(R.id.account_display_name);
        TextView note = (TextView) findViewById(R.id.account_note);
        NetworkImageView avatar = (NetworkImageView) findViewById(R.id.account_avatar);
        NetworkImageView header = (NetworkImageView) findViewById(R.id.account_header);

        String usernameFormatted = String.format(
                getString(R.string.status_username_format), account.username);
        username.setText(usernameFormatted);

        displayName.setText(account.displayName);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(account.displayName);
        }

        note.setText(account.note);
        note.setLinksClickable(true);

        ImageLoader imageLoader = VolleySingleton.getInstance(this).getImageLoader();
        if (!account.avatar.isEmpty()) {
            avatar.setImageUrl(account.avatar, imageLoader);
        }
        if (!account.header.isEmpty()) {
            header.setImageUrl(account.header, imageLoader);
        }

        openInWebUrl = account.url;

        // Add counts to the tabs in the TabLayout.
        String[] counts = {
            account.statusesCount,
            account.followingCount,
            account.followersCount,
        };
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                View view = tab.getCustomView();
                TextView total = (TextView) view.findViewById(R.id.total);
                total.setText(counts[i]);
            }
        }
    }

    private void onObtainAccountFailure() {
        //TODO: help
        Log.e(TAG, "Failed to obtain that account.");
    }

    private void obtainRelationships() {
        String endpoint = getString(R.string.endpoint_relationships);
        String url = String.format("https://%s%s?id=%s", domain, endpoint, accountId);
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        boolean following;
                        boolean blocking;
                        try {
                            JSONObject object = response.getJSONObject(0);
                            following = object.getBoolean("following");
                            blocking = object.getBoolean("blocking");
                        } catch (JSONException e) {
                            onObtainRelationshipsFailure();
                            return;
                        }
                        onObtainRelationshipsSuccess(following, blocking);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onObtainRelationshipsFailure();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onObtainRelationshipsSuccess(boolean following, boolean blocking) {
        this.following = following;
        this.blocking = blocking;
        if (!following || !blocking) {
            invalidateOptionsMenu();
        }
    }

    private void onObtainRelationshipsFailure() {
        //TODO: help
        Log.e(TAG, "Could not obtain relationships?");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!isSelf) {
            MenuItem follow = menu.findItem(R.id.action_follow);
            String title;
            if (following) {
                title = getString(R.string.action_unfollow);
            } else {
                title = getString(R.string.action_follow);
            }
            follow.setTitle(title);
            MenuItem block = menu.findItem(R.id.action_block);
            if (blocking) {
                title = getString(R.string.action_unblock);
            } else {
                title = getString(R.string.action_block);
            }
            block.setTitle(title);
        } else {
            // It shouldn't be possible to block or follow yourself.
            menu.removeItem(R.id.action_follow);
            menu.removeItem(R.id.action_block);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void postRequest(String endpoint, Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener) {
        String url = "https://" + domain + endpoint;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, null, listener,
                errorListener) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void follow(final String id) {
        int endpointId;
        if (following) {
            endpointId = R.string.endpoint_unfollow;
        } else {
            endpointId = R.string.endpoint_follow;
        }
        postRequest(String.format(getString(endpointId), id),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        boolean followingValue;
                        try {
                            followingValue = response.getBoolean("following");
                        } catch (JSONException e) {
                            onFollowFailure(id);
                            return;
                        }
                        following = followingValue;
                        invalidateOptionsMenu();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onFollowFailure(id);
                    }
                });
    }

    private void onFollowFailure(final String id) {
        int messageId;
        if (following) {
            messageId = R.string.error_unfollowing;
        } else {
            messageId = R.string.error_following;
        }
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                follow(id);
            }
        };
        Snackbar.make(findViewById(R.id.activity_account), messageId, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }

    private void block(final String id) {
        int endpointId;
        if (blocking) {
            endpointId = R.string.endpoint_unblock;
        } else {
            endpointId = R.string.endpoint_block;
        }
        postRequest(String.format(getString(endpointId), id),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        boolean blockingValue;
                        try {
                            blockingValue = response.getBoolean("blocking");
                        } catch (JSONException e) {
                            onBlockFailure(id);
                            return;
                        }
                        blocking = blockingValue;
                        invalidateOptionsMenu();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onBlockFailure(id);
                    }
                });
    }

    private void onBlockFailure(final String id) {
        int messageId;
        if (blocking) {
            messageId = R.string.error_unblocking;
        } else {
            messageId = R.string.error_blocking;
        }
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                block(id);
            }
        };
        Snackbar.make(findViewById(R.id.activity_account), messageId, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_back: {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            }
            case R.id.action_open_in_web: {
                Uri uri = Uri.parse(openInWebUrl);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
            case R.id.action_follow: {
                follow(accountId);
                return true;
            }
            case R.id.action_block: {
                block(accountId);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
