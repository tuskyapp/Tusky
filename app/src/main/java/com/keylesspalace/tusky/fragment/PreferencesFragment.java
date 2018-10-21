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

import android.content.SharedPreferences;
import android.os.Bundle;

import android.support.annotation.XmlRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.keylesspalace.tusky.PreferencesActivity;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent;
import com.keylesspalace.tusky.di.Injectable;

import java.util.regex.Pattern;

import javax.inject.Inject;

public class PreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, Injectable {

    @Inject
    EventHub eventHub;

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
    public void onCreatePreferences(Bundle bundle, String s) {
        int preference = getArguments().getInt("preference");

        addPreferencesFromResource(preference);

        sharedPreferences = getPreferenceManager().getSharedPreferences();

        Preference regexPref = findPreference("tabFilterRegex");
        if(regexPref != null) {

            regexPref.setSummary(sharedPreferences.getString("tabFilterRegex", ""));
            regexPref.setOnPreferenceClickListener(preference1 -> {

                EditText editText = new EditText(getContext());
                editText.setText(sharedPreferences.getString("tabFilterRegex", ""));

                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.pref_title_filter_regex)
                        .setView(editText)
                        .setPositiveButton(android.R.string.ok, (dialog1, which) -> {
                            sharedPreferences
                                .edit()
                                .putString("tabFilterRegex", editText.getText().toString())
                                .apply();
                            regexPref.setSummary(editText.getText().toString());
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();

                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s1) {
                        try {
                            Pattern.compile(s1.toString());
                            editText.setError(null);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        } catch (IllegalArgumentException e) {
                            editText.setError(getString(R.string.error_invalid_regex));
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        }
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s1, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s1, int start, int before, int count) {
                    }
                });
                dialog.show();
                return true;
            });
        }

        Preference timelineFilterPreferences = findPreference("timelineFilterPreferences");
        if (timelineFilterPreferences != null) {
            timelineFilterPreferences.setOnPreferenceClickListener(pref -> {
                PreferencesActivity activity = (PreferencesActivity) getActivity();
                if (activity != null) {
                    activity.showFragment(R.xml.timeline_filter_preferences, R.string.pref_title_status_tabs);
                }

                return true;
            });
        }

        Preference httpProxyPreferences = findPreference("httpProxyPreferences");
        if (httpProxyPreferences != null) {
            httpProxyPreferences.setOnPreferenceClickListener(pref -> {
                PreferencesActivity activity = (PreferencesActivity) getActivity();
                if (activity != null) {
                    pendingRestart = false;
                    activity.showFragment(R.xml.http_proxy_preferences, R.string.pref_title_http_proxy_settings);
                }

                return true;
            });
        }

    }



    @Override
    public void onResume() {
        super.onResume();

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

        eventHub.dispatch(new PreferenceChangedEvent(key));

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
