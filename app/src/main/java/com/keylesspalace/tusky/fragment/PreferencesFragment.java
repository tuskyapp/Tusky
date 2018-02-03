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
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.XmlRes;

import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.PreferencesActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.TuskyApplication;
import com.keylesspalace.tusky.db.AccountEntity;

public class PreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    SharedPreferences sharedPreferences;
    static boolean httpProxyChanged = false;
    static boolean pendingRestart = false;

    public static PreferencesFragment newInstance(@XmlRes int preference) {
        PreferencesFragment fragment = new PreferencesFragment();

        Bundle args = new Bundle();
        args.putInt("preference", preference);

        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int preference = getArguments().getInt("preference");

        addPreferencesFromResource(preference);


        Preference notificationPreferences  = findPreference("notificationPreferences");

        if(notificationPreferences != null) {

            AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && activeAccount != null) {
                notificationPreferences.setSummary(getString(R.string.pref_summary_notifications, activeAccount.getFullName()));
            }


            //on Android O and newer, launch the system notification settings instead of the app settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                notificationPreferences.setOnPreferenceClickListener(pref -> {
                    Intent intent = new Intent();
                    intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");

                    intent.putExtra("android.provider.extra.APP_PACKAGE", BuildConfig.APPLICATION_ID);

                    startActivity(intent);
                    return true;
                });

            } else {
                notificationPreferences.setOnPreferenceClickListener(pref -> {
                    PreferencesActivity activity = (PreferencesActivity) getActivity();
                    if (activity != null) {
                        activity.showFragment(R.xml.notification_preferences, R.string.pref_title_edit_notification_settings);
                    }

                    return true;
                });
            }
        }

        Preference timelineFilterPreferences  = findPreference("timelineFilterPreferences");
        if(timelineFilterPreferences != null) {
            timelineFilterPreferences.setOnPreferenceClickListener(pref -> {
                PreferencesActivity activity = (PreferencesActivity) getActivity();
                if (activity != null) {
                    activity.showFragment(R.xml.timeline_filter_preferences, R.string.pref_title_status_tabs);
                }

                return true;
            });
        }

        Preference httpProxyPreferences  = findPreference("httpProxyPreferences");
        if(httpProxyPreferences != null) {
            httpProxyPreferences.setOnPreferenceClickListener(pref -> {
                PreferencesActivity activity = (PreferencesActivity) getActivity();
                if (activity != null) {
                    pendingRestart = false;
                    activity.showFragment(R.xml.http_proxy_preferences, R.string.pref_title_http_proxy_settings);
                }

                return true;
            });
        }

        if(preference == R.xml.notification_preferences) {

            AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

            if(activeAccount != null) {

                CheckBoxPreference notificationPref = (CheckBoxPreference) findPreference("notificationsEnabled");
                notificationPref.setChecked(activeAccount.getNotificationsEnabled());

                CheckBoxPreference mentionedPref = (CheckBoxPreference) findPreference("notificationFilterMentions");
                mentionedPref.setChecked(activeAccount.getNotificationsMentioned());

                CheckBoxPreference followedPref = (CheckBoxPreference) findPreference("notificationFilterFollows");
                followedPref.setChecked(activeAccount.getNotificationsFollowed());

                CheckBoxPreference boostedPref = (CheckBoxPreference) findPreference("notificationFilterReblogs");
                boostedPref.setChecked(activeAccount.getNotificationsReblogged());

                CheckBoxPreference favoritedPref = (CheckBoxPreference) findPreference("notificationFilterFavourites");
                favoritedPref.setChecked(activeAccount.getNotificationsFavorited());

                CheckBoxPreference soundPref = (CheckBoxPreference) findPreference("notificationAlertSound");
                soundPref.setChecked(activeAccount.getNotificationSound());

                CheckBoxPreference vibrationPref = (CheckBoxPreference) findPreference("notificationAlertVibrate");
                vibrationPref.setChecked(activeAccount.getNotificationVibration());

                CheckBoxPreference lightPref = (CheckBoxPreference) findPreference("notificationAlertLight");
                lightPref.setChecked(activeAccount.getNotificationLight());
            }
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        sharedPreferences = getPreferenceManager().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        updateSummary("httpProxyServer");
        updateSummary("httpProxyPort");
        updateHttpProxySummary();
    }

    @Override
    public void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
        if (pendingRestart) {
            pendingRestart = false;
            httpProxyChanged = false;
            System.exit(0);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {

        switch (key) {
            case "httpProxyServer":
            case "httpProxyPort":
                updateSummary(key);
            case "httpProxyEnabled":
                httpProxyChanged = true;
                return;
            default:
        }

        AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

        if(activeAccount != null) {
            switch(key) {
                case "notificationsEnabled":
                    activeAccount.setNotificationsEnabled(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationFilterMentions":
                    activeAccount.setNotificationsMentioned(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationFilterFollows":
                    activeAccount.setNotificationsFollowed(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationFilterReblogs":
                    activeAccount.setNotificationsReblogged(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationFilterFavourites":
                    activeAccount.setNotificationsFavorited(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationAlertSound":
                    activeAccount.setNotificationSound(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationAlertVibrate":
                    activeAccount.setNotificationVibration(sharedPreferences.getBoolean(key, true));
                    break;
                case "notificationAlertLight":
                    activeAccount.setNotificationLight(sharedPreferences.getBoolean(key, true));
                    break;
            }
            TuskyApplication.getAccountManager().saveAccount(activeAccount);

        }

    }

    private void updateSummary(String key) {
        switch (key) {
            case "httpProxyServer":
            case "httpProxyPort":
                EditTextPreference editTextPreference = (EditTextPreference) findPreference(key);
                if (editTextPreference != null) {
                    editTextPreference.setSummary(editTextPreference.getText());
                }
                break;
            default:
        }
    }

    private void updateHttpProxySummary() {
        Preference httpProxyPref = findPreference("httpProxyPreferences");
        if (httpProxyPref != null) {
            if (httpProxyChanged) {
                pendingRestart = true;
            }

            Boolean httpProxyEnabled = sharedPreferences.getBoolean("httpProxyEnabled", false);

            String httpServer = sharedPreferences.getString("httpProxyServer", "");
            int httpPort = Integer.parseInt(sharedPreferences.getString("httpProxyPort", "-1"));

            if (httpProxyEnabled && !httpServer.isEmpty() && (httpPort > 0 && httpPort < 65535)) {
                httpProxyPref.setSummary(httpServer + ":" + httpPort);
            } else {
                httpProxyPref.setSummary("");
            }
        }
    }
}
