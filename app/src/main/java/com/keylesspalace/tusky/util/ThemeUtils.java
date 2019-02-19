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
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.TypedValue;
import android.widget.ImageView;

/**
 * Provides runtime compatibility to obtain theme information and re-theme views, especially where
 * the ability to do so is not supported in resource files.
 */
public class ThemeUtils {
    public static final String APP_THEME_DEFAULT = ThemeUtils.THEME_NIGHT;

    private static final String THEME_NIGHT = "night";
    private static final String THEME_DAY = "day";
    private static final String THEME_BLACK = "black";
    private static final String THEME_AUTO = "auto";
    private static final String THEME_SYSTEM = "system";

    public static Drawable getDrawable(@NonNull Context context, @AttrRes int attribute,
            @DrawableRes int fallbackDrawable) {
        TypedValue value = new TypedValue();
        @DrawableRes int resourceId;
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            resourceId = value.resourceId;
        } else {
            resourceId = fallbackDrawable;
        }
        return context.getDrawable(resourceId);
    }

    public static @DrawableRes int getDrawableId(@NonNull Context context, @AttrRes int attribute,
            @DrawableRes int fallbackDrawableId) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            return value.resourceId;
        } else {
            return fallbackDrawableId;
        }
    }

    public static @ColorInt int getColor(@NonNull Context context, @AttrRes int attribute) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            return value.data;
        } else {
            return Color.BLACK;
        }
    }

    public static @ColorRes int getColorId(@NonNull Context context, @AttrRes int attribute) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId;
    }

    public static @ColorInt int getColorById(@NonNull Context context, String name) {
        return getColor(context,
                ResourcesUtils.getResourceIdentifier(context, "attr", name));
    }

    public static void setImageViewTint(ImageView view, @AttrRes int attribute) {
        view.setColorFilter(getColor(view.getContext(), attribute), PorterDuff.Mode.SRC_IN);
    }

    /** this can be replaced with drawableTint in xml once minSdkVersion >= 23 */
    public static @Nullable Drawable getTintedDrawable(@NonNull Context context, @DrawableRes int drawableId, @AttrRes int colorAttr) {
        Drawable drawable = context.getDrawable(drawableId);
        if(drawable == null) {
            return null;
        }
        setDrawableTint(context, drawable, colorAttr);
        return drawable;
    }

    public static void setDrawableTint(Context context, Drawable drawable, @AttrRes int attribute) {
        drawable.setColorFilter(getColor(context, attribute), PorterDuff.Mode.SRC_IN);
    }

    public static void setAppNightMode(String flavor, Context context) {
        int mode;
        switch (flavor) {
            default:
            case THEME_NIGHT:
                mode = UiModeManager.MODE_NIGHT_YES;
                break;
            case THEME_DAY:
                mode = UiModeManager.MODE_NIGHT_NO;
                break;
            case THEME_BLACK:
                mode = UiModeManager.MODE_NIGHT_YES;
                break;
            case THEME_AUTO:
                mode = UiModeManager.MODE_NIGHT_AUTO;
                break;
            case THEME_SYSTEM:
                mode = -1; //https://developer.android.com/reference/android/support/v7/app/AppCompatDelegate.html#mode_night_follow_system
                break;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UiModeManager uiModeManager = (UiModeManager)context.getApplicationContext().getSystemService(Context.UI_MODE_SERVICE);
            uiModeManager.setNightMode(mode);
        }


        AppCompatDelegate.setDefaultNightMode(mode);

    }
}
