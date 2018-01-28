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

import android.app.UiModeManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatDelegate;
import android.util.TypedValue;
import android.widget.ImageView;

import com.keylesspalace.tusky.TuskyApplication;

/**
 * Provides runtime compatibility to obtain theme information and re-theme views, especially where
 * the ability to do so is not supported in resource files.
 */
public class ThemeUtils {
    public static final String THEME_MODE_PREFER = "prefer";
    public static final String THEME_MODE_ONLY = "only";
    public static final String THEME_FLAVOR_NIGHT = "night";
    public static final String THEME_FLAVOR_DAY = "day";
    public static final String THEME_FLAVOR_AUTO = "auto";
    public static final String THEME_FLAVOR_DEFAULT = "preferred";

    public static Drawable getDrawable(Context context, @AttrRes int attribute,
            @DrawableRes int fallbackDrawable) {
        TypedValue value = new TypedValue();
        @DrawableRes int resourceId;
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            resourceId = value.resourceId;
        } else {
            resourceId = fallbackDrawable;
        }
        return ContextCompat.getDrawable(context, resourceId);
    }

    public static @DrawableRes int getDrawableId(Context context, @AttrRes int attribute,
            @DrawableRes int fallbackDrawableId) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            return value.resourceId;
        } else {
            return fallbackDrawableId;
        }
    }

    public static @ColorInt int getColor(Context context, @AttrRes int attribute) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            return value.data;
        } else {
            return Color.BLACK;
        }
    }

    public static @ColorRes int getColorId(Context context, @AttrRes int attribute) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId;
    }

    public static @ColorInt int getColorById(Context context, String name) {
        return getColor(context,
                ResourcesUtils.getResourceIdentifier(context, "attr", name));
    }

    public static void setImageViewTint(ImageView view, @AttrRes int attribute) {
        view.setColorFilter(getColor(view.getContext(), attribute), PorterDuff.Mode.SRC_IN);
    }

    public static void setDrawableTint(Context context, Drawable drawable, @AttrRes int attribute) {
        drawable.setColorFilter(getColor(context, attribute), PorterDuff.Mode.SRC_IN);
    }

    public static boolean setAppNightMode(String flavor) {
        int mode;
        switch (flavor) {
            case THEME_FLAVOR_AUTO:
                mode = UiModeManager.MODE_NIGHT_AUTO;
                break;
            case THEME_FLAVOR_NIGHT:
                mode = UiModeManager.MODE_NIGHT_YES;
                break;
            case THEME_FLAVOR_DAY:
                mode = UiModeManager.MODE_NIGHT_NO;
                break;
            default:
                return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TuskyApplication.getUiModeManager().setNightMode(mode);
        } else {
            AppCompatDelegate.setDefaultNightMode(mode);
        }

        return true;
    }
}
