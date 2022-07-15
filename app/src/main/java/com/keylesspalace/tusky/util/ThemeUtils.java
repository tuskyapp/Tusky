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

package com.keylesspalace.tusky.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Provides runtime compatibility to obtain theme information and re-theme views, especially where
 * the ability to do so is not supported in resource files.
 */
public class ThemeUtils {

    public static final String APP_THEME_DEFAULT = ThemeUtils.THEME_NIGHT;

    public static final String THEME_NIGHT = "night";
    public static final String THEME_DAY = "day";
    public static final String THEME_BLACK = "black";
    public static final String THEME_MATERIAL_YOU_LIGHT = "material_you_light";
    public static final String THEME_MATERIAL_YOU_DARK = "material_you_dark";
    public static final String THEME_AUTO = "auto";
    public static final String THEME_SYSTEM = "auto_system";

    @ColorInt
    public static int getColor(@NonNull Context context, @AttrRes int attribute) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            return value.data;
        } else {
            return Color.BLACK;
        }
    }

    public static int getDimension(@NonNull Context context, @AttrRes int attribute) {
        TypedArray array = context.obtainStyledAttributes(new int[] { attribute });
        int dimen = array.getDimensionPixelSize(0, -1);
        array.recycle();
        return dimen;
    }

    public static void setDrawableTint(Context context, Drawable drawable, @AttrRes int attribute) {
        drawable.setColorFilter(getColor(context, attribute), PorterDuff.Mode.SRC_IN);
    }

    public static void setAppNightMode(String flavor) {
        switch (flavor) {
            default:
            case THEME_NIGHT:
            case THEME_BLACK:
            case THEME_MATERIAL_YOU_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_DAY:
            case THEME_MATERIAL_YOU_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_AUTO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME);
                break;
            case THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
