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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

public class PreferencesActivity extends BaseActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private boolean themeSwitched;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            themeSwitched = savedInstanceState.getBoolean("themeSwitched");
        } else {
            Bundle extras = getIntent().getExtras();
            themeSwitched = extras != null && extras.getBoolean("themeSwitched");
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean("lightTheme", false)) {
            setTheme(R.style.AppTheme_Light);
        }
        preferences.registerOnSharedPreferenceChangeListener(this);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PreferencesFragment())
                .commit();
    }

    private void saveInstanceState(Bundle outState) {
        outState.putBoolean("themeSwitched", themeSwitched);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("lightTheme")) {
            themeSwitched = true;
            // recreate() could be used instead, but it doesn't have an animation B).
            Intent intent = getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle savedInstanceState = new Bundle();
            saveInstanceState(savedInstanceState);
            intent.putExtras(savedInstanceState);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void onBackPressed() {
        /* Switching themes won't actually change the theme of activities on the back stack.
         * Either the back stack activities need to all be recreated, or do the easier thing, which
         * is hijack the back button press and use it to launch a new MainActivity and clear the
         * back stack. */
        if (themeSwitched) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            super.onBackPressed();
        }
    }
}
