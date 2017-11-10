package com.keylesspalace.tusky.util;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

public class CustomURLSpan extends URLSpan {
    public CustomURLSpan(String url) {
        super(url);
    }

    private CustomURLSpan(Parcel src) {
        super(src);
    }

    public static final Parcelable.Creator<CustomURLSpan> CREATOR = new Parcelable.Creator<CustomURLSpan>() {

        @Override
        public CustomURLSpan createFromParcel(Parcel source) {
            return new CustomURLSpan(source);
        }

        @Override
        public CustomURLSpan[] newArray(int size) {
            return new CustomURLSpan[size];
        }

    };

    @Override
    public void onClick(View view) {
        LinkHelper.openLink(getURL(), view.getContext());
    }

    @Override public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(false);
    }
}
