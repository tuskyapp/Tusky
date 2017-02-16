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

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.Menu;

/* There isn't presently a way to globally change the theme of a whole application at runtime, just
 * individual activities. So, each activity has to set its theme before any views are created. And
 * the most expedient way to accomplish this was to put it in a base class and just have every
 * activity extend from it. */
public class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("lightTheme", false)) {
            setTheme(R.style.AppTheme_Light);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        TypedValue value = new TypedValue();
        int color;
        if (getTheme().resolveAttribute(R.attr.toolbar_icon_tint, value, true)) {
            color = value.data;
        } else {
            color = Color.WHITE;
        }
        for (int i = 0; i < menu.size(); i++) {
            Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) {
                icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }
}
