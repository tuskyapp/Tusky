package com.keylesspalace.tusky.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;

import com.keylesspalace.tusky.R;

class CustomTabURLSpan extends URLSpan {
    CustomTabURLSpan(String url) {
        super(url);
    }

    private CustomTabURLSpan(Parcel src) {
        super(src);
    }

    public static final Parcelable.Creator<CustomTabURLSpan> CREATOR = new Parcelable.Creator<CustomTabURLSpan>() {

        @Override
        public CustomTabURLSpan createFromParcel(Parcel source) {
            return new CustomTabURLSpan(source);
        }

        @Override
        public CustomTabURLSpan[] newArray(int size) {
            return new CustomTabURLSpan[size];
        }
    };

    @Override
    public void onClick(View widget) {
        Uri uri = Uri.parse(getURL());
        Context context = widget.getContext();
        boolean lightTheme = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("lightTheme", false);
        int toolbarColor = ContextCompat.getColor(context, lightTheme ? R.color.custom_tab_toolbar_light : R.color.custom_tab_toolbar_dark);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(toolbarColor);
        CustomTabsIntent customTabsIntent = builder.build();
        try {
            String packageName = CustomTabsHelper.getPackageNameToUse(context);

            //If we cant find a package name, it means theres no browser that supports
            //Chrome Custom Tabs installed. So, we fallback to the webview
            if (packageName == null) {
                super.onClick(widget);
            } else {
                customTabsIntent.intent.setPackage(packageName);
                customTabsIntent.launchUrl(context, uri);
            }
        } catch (ActivityNotFoundException e) {
            Log.w("URLSpan", "Activity was not found for intent, " + customTabsIntent.toString());
        }
    }
}
