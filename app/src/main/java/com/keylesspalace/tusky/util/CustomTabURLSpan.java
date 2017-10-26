package com.keylesspalace.tusky.util;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.style.URLSpan;
import android.view.View;

public class CustomTabURLSpan extends URLSpan {
    public CustomTabURLSpan(String url) {
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
    public void onClick(View view) {
        Uri uri = Uri.parse(getURL());
        LinkHelper.openLinkInCustomTab(uri, view.getContext());
    }
}
