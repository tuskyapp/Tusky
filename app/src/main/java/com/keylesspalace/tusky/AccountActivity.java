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
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
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
    private String domain;
    private String accessToken;
    private boolean following = false;
    private boolean blocking = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        Intent intent = getIntent();
        String username = intent.getStringExtra("username");
        String id = intent.getStringExtra("id");
        TextView accountName = (TextView) findViewById(R.id.account_username);
        accountName.setText(username);

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        assert(domain != null);
        assert(accessToken != null);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NetworkImageView avatar = (NetworkImageView) findViewById(R.id.account_avatar);
        NetworkImageView header = (NetworkImageView) findViewById(R.id.account_header);
        avatar.setDefaultImageResId(R.drawable.avatar_default);
        avatar.setErrorImageResId(R.drawable.avatar_error);
        header.setDefaultImageResId(R.drawable.account_header_default);

        obtainAccount(id);
        obtainRelationships(id);

        // Setup the tabs and timeline pager.
        AccountPagerAdapter adapter = new AccountPagerAdapter(getSupportFragmentManager(), id);
        String[] pageTitles = {
                getString(R.string.title_statuses),
                getString(R.string.title_follows),
                getString(R.string.title_followers)
        };
        adapter.setPageTitles(pageTitles);
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(adapter);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void obtainAccount(String id) {
        String endpoint = String.format(getString(R.string.endpoint_accounts), id);
        String url = "https://" + domain + endpoint;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            onObtainAccountSuccess(response);
                        } catch (JSONException e) {
                            onObtainAccountFailure();
                        }
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

    private void onObtainAccountSuccess(JSONObject response) throws JSONException {
        TextView username = (TextView) findViewById(R.id.account_username);
        TextView displayName = (TextView) findViewById(R.id.account_display_name);
        TextView note = (TextView) findViewById(R.id.account_note);
        NetworkImageView avatar = (NetworkImageView) findViewById(R.id.account_avatar);
        NetworkImageView header = (NetworkImageView) findViewById(R.id.account_header);

        String usernameFormatted = String.format(
                getString(R.string.status_username_format), response.getString("acct"));
        username.setText(usernameFormatted);

        String displayNameString = response.getString("display_name");
        displayName.setText(displayNameString);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(displayNameString);
        }

        String noteHtml = response.getString("note");
        Spanned noteSpanned = HtmlUtils.fromHtml(noteHtml);
        note.setText(noteSpanned);

        ImageLoader imageLoader = VolleySingleton.getInstance(this).getImageLoader();
        avatar.setImageUrl(response.getString("avatar"), imageLoader);
        String headerUrl = response.getString("header");
        if (!headerUrl.isEmpty() && !headerUrl.equals("/headers/original/missing.png")) {
            header.setImageUrl(headerUrl, imageLoader);
        }
    }

    private void onObtainAccountFailure() {
        //TODO: help
        assert(false);
    }

    private void obtainRelationships(String id) {
        String endpoint = getString(R.string.endpoint_relationships);
        String url = String.format("https://%s%s?id=%s", domain, endpoint, id);
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
        assert(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
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
        return super.onPrepareOptionsMenu(menu);
    }

    private void follow() {

    }

    private void block() {

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
            case R.id.action_follow: {
                follow();
                return true;
            }
            case R.id.action_block: {
                block();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
