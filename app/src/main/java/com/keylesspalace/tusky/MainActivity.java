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
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
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

public class MainActivity extends AppCompatActivity {
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
        TimelinePagerAdapter adapter = new TimelinePagerAdapter(getSupportFragmentManager());
        String[] pageTitles = {
            getString(R.string.title_home),
            getString(R.string.title_notifications),
            getString(R.string.title_public)
        };
        adapter.setPageTitles(pageTitles);
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        int pageMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                getResources().getDisplayMetrics());
        viewPager.setPageMargin(pageMargin);
        viewPager.setPageMarginDrawable(R.drawable.tab_page_margin);
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

        /* @Unused: for Firebase Push Notifications
        Log.d(TAG, "token " + FirebaseInstanceId.getInstance().getToken());

        // Check if it's necessary to register for push notifications for this instance.
        boolean registered = preferences.getBoolean("firebaseRegistered", false);
        if (!registered) {
            String registrationId = preferences.getString("firebaseRegistrationId", null);
            if (registrationId == null) {
                registrationId = FirebaseInstanceId.getInstance().getToken();
            }
            sendRegistrationToServer(registrationId, true);
        }
        */
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
                                onFetchUserInfoFailure();
                                return;
                            }
                            onFetchUserInfoSuccess(id, username);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            onFetchUserInfoFailure();
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

    private void onFetchUserInfoFailure() {
        //TODO: help
        Log.e(TAG, "Failed to fetch the logged-in user's info.");
    }

    /* @Unused: For Firebase push notifications, useless for now.
    private void sendRegistrationToServer(String token, final boolean register) {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String domain = preferences.getString("domain", null);
        final String accessToken = preferences.getString("accessToken", null);

        String endpoint;
        if (register) {
            endpoint = getString(R.string.endpoint_devices_register);
        } else {
            endpoint = getString(R.string.endpoint_devices_unregister);
        }
        String url = "https://" + domain + endpoint;
        JSONObject formData = new JSONObject();
        try {
            formData.put("registration_id", token);
        } catch (JSONException e) {
            onSendRegistrationToServerFailure();
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, formData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        onSendRegistrationToServerSuccess(response, register);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onSendRegistrationToServerFailure();
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

    private void onSendRegistrationToServerSuccess(JSONObject response, boolean register) {
        String registeredWord;
        if (register) {
            registeredWord = "registration";
        } else {
            registeredWord = "unregistration";
        }
        Log.d(TAG, String.format("Firebase %s is confirmed with the Mastodon instance. %s",
                registeredWord, response.toString()));
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("firebaseRegistered", register);
        editor.apply();
    }

    private void onSendRegistrationToServerFailure() {
        Log.d(TAG, "Firebase registration with the Mastodon instance failed");
    }
    */

    private void compose() {
        Intent intent = new Intent(this, ComposeActivity.class);
        startActivity(intent);
    }

    private void viewProfile() {
        Intent intent = new Intent(this, AccountActivity.class);
        intent.putExtra("id", loggedInAccountId);
        startActivity(intent);
    }

    private void viewPreferences() {
        Intent intent = new Intent(this, PreferencesActivity.class);
        startActivity(intent);
    }

    private void logOut() {
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
                compose();
                return true;
            }
            case R.id.action_profile: {
                viewProfile();
                return true;
            }
            case R.id.action_preferences: {
                viewPreferences();
                return true;
            }
            case R.id.action_logout: {
                logOut();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
