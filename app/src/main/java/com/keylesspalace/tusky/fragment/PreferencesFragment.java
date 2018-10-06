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

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.XmlRes;
import android.text.Editable;
import android.text.TextWatcher;

import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.SettingsActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.TuskyApplication;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;

import java.util.regex.Pattern;

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

    private AccountManager accountManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        accountManager = TuskyApplication.getInstance(getActivity()).getServiceLocator()
                .get(AccountManager.class);


        int preference = getArguments().getInt("preference");

        addPreferencesFromResource(preference);

        Preference regexPref = findPreference("tabFilterRegex");
        if (regexPref != null) regexPref.setOnPreferenceClickListener(pref -> {
            // Reset the error dialog when shown; if the dialog was closed with the cancel button
            // while an invalid regex was present, this would otherwise cause buggy behaviour.
            ((EditTextPreference) regexPref).getEditText().setError(null);

            // Test the regex as the user inputs text, ensuring immediate feedback and preventing
            // setting of an invalid regex, which would cause a crash loop.
            ((EditTextPreference) regexPref).getEditText().addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        Pattern.compile(s.toString());
                        ((EditTextPreference) regexPref).getEditText().setError(null);
                        AlertDialog dialog = (AlertDialog) ((EditTextPreference) pref).getDialog();
                        if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    } catch (IllegalArgumentException e) {
                        ((AlertDialog) ((EditTextPreference) pref).getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        ((EditTextPreference) regexPref).getEditText().setError(getString(R.string.error_invalid_regex));
                    }
                }
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });
            return false;
        });

        Preference notificationPreferences = findPreference("notificationPreferences");

        if (notificationPreferences != null) {

            AccountEntity activeAccount = accountManager.getActiveAccount();

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
                    SettingsActivity activity = (SettingsActivity) getActivity();
                    if (activity != null) {
                        activity.showFragment(R.xml.notification_preferences, R.string.pref_title_edit_notification_settings);
                    }

                    return true;
                });
            }
        }

        Preference timelineFilterPreferences = findPreference("timelineFilterPreferences");
        if (timelineFilterPreferences != null) {
            timelineFilterPreferences.setOnPreferenceClickListener(pref -> {
                SettingsActivity activity = (SettingsActivity) getActivity();
                if (activity != null) {
                    activity.showFragment(R.xml.timeline_filter_preferences, R.string.pref_title_status_tabs);
                }

                return true;
            });
        }

        Preference httpProxyPreferences = findPreference("httpProxyPreferences");
        if (httpProxyPreferences != null) {
            httpProxyPreferences.setOnPreferenceClickListener(pref -> {
                SettingsActivity activity = (SettingsActivity) getActivity();
                if (activity != null) {
                    pendingRestart = false;
                    activity.showFragment(R.xml.http_proxy_preferences, R.string.pref_title_http_proxy_settings);
                }

                return true;
            });
        }

        if (preference == R.xml.notification_preferences) {

            AccountEntity activeAccount = accountManager.getActiveAccount();

            if (activeAccount != null) {

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

        AccountEntity activeAccount = accountManager.getActiveAccount();

        if (activeAccount != null) {
            switch (key) {
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
            accountManager.saveAccount(activeAccount);

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

            try {
                int httpPort = Integer.parseInt(sharedPreferences.getString("httpProxyPort", "-1"));

                if (httpProxyEnabled && !httpServer.isEmpty() && (httpPort > 0 && httpPort < 65535)) {
                    httpProxyPref.setSummary(httpServer + ":" + httpPort);
                    return;
                }
            } catch (NumberFormatException e) {
                // user has entered wrong port, fall back to empty summary
            }

            httpProxyPref.setSummary("");

        }
    }
}
