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

package com.keylesspalace.tusky;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.XmlRes;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.keylesspalace.tusky.fragment.PreferencesFragment;
import com.keylesspalace.tusky.util.ResourcesUtils;
import com.keylesspalace.tusky.util.ThemeUtils;

public class PreferencesActivity extends BaseActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private boolean restartActivitiesOnExit;
    private @XmlRes int currentPreferences;
    private @StringRes int currentTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            restartActivitiesOnExit = savedInstanceState.getBoolean("restart");
        } else {
            Bundle extras = getIntent().getExtras();
            restartActivitiesOnExit = extras != null && extras.getBoolean("restart");
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.activity_preferences);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }


        preferences.registerOnSharedPreferenceChangeListener(this);

        if(savedInstanceState == null) {
            currentPreferences = R.xml.preferences;
            currentTitle = R.string.action_view_preferences;
        } else {
            currentPreferences = savedInstanceState.getInt("preferences");
            currentTitle = savedInstanceState.getInt("title");
        }
        showFragment(currentPreferences, currentTitle);

        PreferencesFragment preferencesFragment = (PreferencesFragment)getFragmentManager().findFragmentById(R.id.fragment_container);
        String[] themeFlavorPair = preferences.getString("appTheme", "AppTheme:prefer:night").split(":");
        String appTheme = themeFlavorPair[0], themeFlavorMode = themeFlavorPair[1], themeFlavorPreference = themeFlavorPair[2];

        setTheme(ResourcesUtils.getResourceIdentifier(this, "style", appTheme));

        if (preferencesFragment.findPreference("appThemeFlavor") != null) {
            boolean lockFlavor = themeFlavorMode.equals("only");
            preferencesFragment.findPreference("appThemeFlavor").setEnabled(!lockFlavor);
        }

        String flavor = preferences.getString("appThemeFlavor", "preferred");
        if (flavor.equals("preferred")) {
            flavor = themeFlavorPreference;

            preferences.edit()
                    .putString("appThemeFlavor", flavor)
                    .apply();
        }

        final String finalFlavor = flavor;

        if (flavor.equals("auto")) {
            String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION};
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_optional_permission_request))
                        .setMessage(getString(R.string.dialog_twilightmanager_coarse_location_permission_explanation))
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(PreferencesActivity.this, permissions, 1);
                                if (ContextCompat.checkSelfPermission(PreferencesActivity.this,
                                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                                    ThemeUtils.setAppNightMode(finalFlavor);
                            }
                        })
                        .setNegativeButton("Ask me later", null)
                        .show();
            }
        }

        // Set theme based on preference
        setTheme(ResourcesUtils.getResourceIdentifier(this, "style", appTheme));
    }

    public void showFragment(@XmlRes int preferenceId, @StringRes int title) {

        //TODO: cache the Fragments so they can be reused
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, PreferencesFragment.newInstance(preferenceId))
                .commit();

        getFragmentManager().executePendingTransactions();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }

        currentPreferences = preferenceId;
        currentTitle = title;
    }

    private void saveInstanceState(Bundle outState) {
        outState.putBoolean("restart", restartActivitiesOnExit);
        outState.putInt("preferences", currentPreferences);
        outState.putInt("title", currentTitle);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "appTheme": {
                String[] themeFlavorPair = sharedPreferences.getString("appTheme", "AppTheme:prefer:night").split(":");
                String appTheme = themeFlavorPair[0], themeFlavorMode = themeFlavorPair[1], themeFlavorPreference = themeFlavorPair[2];

                setTheme(ResourcesUtils.getResourceIdentifier(this, "style", appTheme));

                sharedPreferences.edit()
                        .remove("appThemeFlavor")
                        .apply();
            }
            case "appThemeFlavor": {
                String[] themeFlavorPair = sharedPreferences.getString("appTheme", "AppTheme:prefer:night").split(":");
                String appTheme = themeFlavorPair[0], themeFlavorMode = themeFlavorPair[1], themeFlavorPreference = themeFlavorPair[2];

                setTheme(ResourcesUtils.getResourceIdentifier(this, "style", appTheme));

                String flavor = sharedPreferences.getString("appThemeFlavor", "preferred");
                if (flavor.equals("preferred")) {
                    flavor = themeFlavorPreference;

                    sharedPreferences.edit()
                            .putString("appThemeFlavor", flavor)
                            .apply();
                }
                ThemeUtils.setAppNightMode(flavor);

                restartActivitiesOnExit = true;
                // recreate() could be used instead, but it doesn't have an animation B).
                Intent intent = getIntent();
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                Bundle savedInstanceState = new Bundle();
                saveInstanceState(savedInstanceState);
                intent.putExtras(savedInstanceState);
                startActivity(intent);
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                break;
            }
            case "statusTextSize": {
                restartActivitiesOnExit = true;
                break;
            }
            case "notificationsEnabled": {
                boolean enabled = sharedPreferences.getBoolean("notificationsEnabled", true);
                if (enabled) {
                    enablePushNotifications();
                } else {
                    disablePushNotifications();
                }
                break;
            }
            case "pullNotificationCheckInterval": {
                String s = sharedPreferences.getString("pullNotificationCheckInterval", "15");
                long minutes = Long.valueOf(s);
                setPullNotificationCheckInterval(minutes);
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        //if we are not on the top level, show the top level. Else exit the activity
        if(currentPreferences != R.xml.preferences) {
            showFragment(R.xml.preferences, R.string.action_view_preferences);

        } else {
        /* Switching themes won't actually change the theme of activities on the back stack.
         * Either the back stack activities need to all be recreated, or do the easier thing, which
         * is hijack the back button press and use it to launch a new MainActivity and clear the
         * back stack. */
            if (restartActivitiesOnExit) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
