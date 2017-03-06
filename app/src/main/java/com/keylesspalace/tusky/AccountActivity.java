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
import android.graphics.drawable.Drawable;
import android.icu.text.NumberFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AccountActivity extends BaseActivity {
    private static final String TAG = "AccountActivity"; // Volley request tag and logging tag

    private String domain;
    private String accessToken;
    private String accountId;
    private boolean following = false;
    private boolean blocking = false;
    private boolean isSelf;
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

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setTitle(null);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        FloatingActionButton floatingBtn = (FloatingActionButton) findViewById(R.id.floating_btn);
        floatingBtn.hide();

        CircularImageView avatar = (CircularImageView) findViewById(R.id.account_avatar);
        ImageView header = (ImageView) findViewById(R.id.account_header);
        avatar.setImageResource(R.drawable.avatar_default);
        header.setImageResource(R.drawable.account_header_default);

        obtainAccount();
        if (!accountId.equals(loggedInAccountId)) {
            isSelf = false;
            obtainRelationships();
        } else {
            /* Cause the options menu to update and instead show an options menu for when the
             * account being shown is their own account. */
            isSelf = true;
            invalidateOptionsMenu();
        }

        // Setup the tabs and timeline pager.
        AccountPagerAdapter adapter = new AccountPagerAdapter(getSupportFragmentManager(), this,
                accountId);
        String[] pageTitles = {
            getString(R.string.title_statuses),
            getString(R.string.title_follows),
            getString(R.string.title_followers)
        };
        adapter.setPageTitles(pageTitles);
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
        viewPager.setPageMarginDrawable(pageMarginDrawable);
        viewPager.setAdapter(adapter);
        tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                tab.setCustomView(adapter.getTabView(i, tabLayout));
            }
        }
    }

    @Override
    protected void onDestroy() {
        VolleySingleton.getInstance(this).cancelAll(TAG);
        super.onDestroy();
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
        request.setTag(TAG);
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onObtainAccountSuccess(Account account) {
        TextView username = (TextView) findViewById(R.id.account_username);
        TextView displayName = (TextView) findViewById(R.id.account_display_name);
        TextView note = (TextView) findViewById(R.id.account_note);
        CircularImageView avatar = (CircularImageView) findViewById(R.id.account_avatar);
        ImageView header = (ImageView) findViewById(R.id.account_header);

        String usernameFormatted = String.format(
                getString(R.string.status_username_format), account.username);
        username.setText(usernameFormatted);

        displayName.setText(account.displayName);

        note.setText(account.note);
        note.setLinksClickable(true);
        note.setMovementMethod(LinkMovementMethod.getInstance());

        if (!account.avatar.isEmpty()) {
            Picasso.with(this)
                    .load(account.avatar)
                    .placeholder(R.drawable.avatar_default)
                    .error(R.drawable.avatar_error)
                    .into(avatar);
        }
        if (!account.header.isEmpty()) {
            Picasso.with(this)
                    .load(account.header)
                    .placeholder(R.drawable.account_header_default)
                    .into(header);
        }

        openInWebUrl = account.url;
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance();

        // Add counts to the tabs in the TabLayout.
        String[] counts = {
            nf.format(Integer.parseInt(account.statusesCount)),
            nf.format(Integer.parseInt(account.followingCount)),
            nf.format(Integer.parseInt(account.followersCount)),
        };

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                View view = tab.getCustomView();
                if (view != null) {
                    TextView total = (TextView) view.findViewById(R.id.total);
                    total.setText(counts[i]);
                }
            }
        }
    }

    private void onObtainAccountFailure() {
        Snackbar.make(tabLayout, R.string.error_obtain_account, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        obtainAccount();
                    }
                })
                .show();
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
                            onObtainRelationshipsFailure(e);
                            return;
                        }
                        onObtainRelationshipsSuccess(following, blocking);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onObtainRelationshipsFailure(error);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        request.setTag(TAG);
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onObtainRelationshipsSuccess(boolean following, boolean blocking) {
        this.following = following;
        this.blocking = blocking;

        if (!following || !blocking) {
            invalidateOptionsMenu();
        }

        updateButtons();
    }

    private void updateButtons() {
        invalidateOptionsMenu();

        FloatingActionButton floatingBtn = (FloatingActionButton) findViewById(R.id.floating_btn);

        if(!isSelf && !blocking) {
            floatingBtn.show();

            if (!following) {
                floatingBtn.setImageResource(R.drawable.ic_person_add_24dp);
            } else {
                floatingBtn.setImageResource(R.drawable.ic_person_outline_24dp);
            }

            floatingBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    follow(accountId);
                }
            });
        } else if(!isSelf && blocking) {
            // TODO: floating button becomes unblock
        }
    }

    private void onObtainRelationshipsFailure(Exception exception) {
        Log.e(TAG, "Could not obtain relationships. " + exception.getMessage());
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
        request.setTag(TAG);
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
                        updateButtons();
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
                        updateButtons();
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
            case android.R.id.home: {
                onBackPressed();
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
