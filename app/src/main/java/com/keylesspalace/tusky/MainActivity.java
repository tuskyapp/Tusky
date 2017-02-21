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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity"; // logging tag

    private AlarmManager alarmManager;
    private PendingIntent serviceAlarmIntent;
    private boolean notificationServiceEnabled;
    private String loggedInAccountId;
    private String loggedInAccountUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fetch user info while we're doing other things.
        fetchUserInfo();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup the tabs and timeline pager.
        TimelinePagerAdapter adapter = new TimelinePagerAdapter(getSupportFragmentManager(), this);
        String[] pageTitles = {
            getString(R.string.title_home),
            getString(R.string.title_notifications),
            getString(R.string.title_public)
        };
        adapter.setPageTitles(pageTitles);
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
            viewPager.setPageMarginDrawable(pageMarginDrawable);
        viewPager.setAdapter(adapter);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        // Retrieve notification update preference.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        notificationServiceEnabled = preferences.getBoolean("pullNotifications", true);
        String minutesString = preferences.getString("pullNotificationCheckInterval", "15");
        long notificationCheckInterval = 60 * 1000 * Integer.valueOf(minutesString);
        // Start up the PullNotificationsService.
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, PullNotificationService.class);
        final int SERVICE_REQUEST_CODE = 8574603; // This number is arbitrary.
        serviceAlarmIntent = PendingIntent.getService(this, SERVICE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (notificationServiceEnabled) {
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime(), notificationCheckInterval, serviceAlarmIntent);
        } else {
            alarmManager.cancel(serviceAlarmIntent);
        }
    }

    private void fetchUserInfo() {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String domain = preferences.getString("domain", null);
        final String accessToken = preferences.getString("accessToken", null);
        String id = preferences.getString("loggedInAccountId", null);
        String username = preferences.getString("loggedInAccountUsername", null);
        if (id != null && username != null) {
            loggedInAccountId = id;
            loggedInAccountUsername = username;
        } else {
            String endpoint = getString(R.string.endpoint_verify_credentials);
            String url = "https://" + domain + endpoint;
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            String username;
                            String id;
                            try {
                                id = response.getString("id");
                                username = response.getString("acct");
                            } catch (JSONException e) {
                                onFetchUserInfoFailure(e);
                                return;
                            }
                            onFetchUserInfoSuccess(id, username);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            onFetchUserInfoFailure(error);
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
    }

    private void onFetchUserInfoSuccess(String id, String username) {
        loggedInAccountId = id;
        loggedInAccountUsername = username;
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("loggedInAccountId", loggedInAccountId);
        editor.putString("loggedInAccountUsername", loggedInAccountUsername);
        editor.apply();
    }

    private void onFetchUserInfoFailure(Exception exception) {
        //TODO: help
        Log.e(TAG, "Failed to fetch user info. " + exception.getMessage());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_compose: {
                Intent intent = new Intent(this, ComposeActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_view_profile: {
                Intent intent = new Intent(this, AccountActivity.class);
                intent.putExtra("id", loggedInAccountId);
                startActivity(intent);
                return true;
            }
            case R.id.action_view_preferences: {
                Intent intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_view_favourites: {
                Intent intent = new Intent(this, FavouritesActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_view_blocks: {
                Intent intent = new Intent(this, BlocksActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_logout: {
                if (notificationServiceEnabled) {
                    alarmManager.cancel(serviceAlarmIntent);
                }
                SharedPreferences preferences = getSharedPreferences(
                        getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.remove("domain");
                editor.remove("accessToken");
                editor.apply();
                Intent intent = new Intent(this, SplashActivity.class);
                startActivity(intent);
                finish();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
