package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomTabURLSpan;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.DateFormat;
import java.util.Date;

class StatusDetailedViewHolder extends StatusBaseViewHolder {
    private TextView reblogs;
    private TextView favourites;
    private TextView application;

    StatusDetailedViewHolder(View view) {
        super(view);
        reblogs = view.findViewById(R.id.status_reblogs);
        favourites = view.findViewById(R.id.status_favourites);
        application = view.findViewById(R.id.status_application);
    }

    @Override
    protected void setCreatedAt(@Nullable Date createdAt) {
        if (createdAt != null) {
            DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(
                    timestamp.getContext());
            timestamp.setText(dateFormat.format(createdAt));
        } else {
            timestamp.setText("");
        }
    }

    private void setApplication(@Nullable Status.Application app) {
        if (app == null) {
            application.setText("");
        } else if (app.website != null) {
            URLSpan span;
            Context context = application.getContext();
            boolean useCustomTabs = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("customTabs", true);
            if (useCustomTabs) {
                span = new CustomTabURLSpan(app.website);
            } else {
                span = new URLSpan(app.website);
            }
            SpannableStringBuilder text = new SpannableStringBuilder(app.name);
            text.setSpan(span, 0, app.name.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            application.setText(text);
            application.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            application.setText(app.name);
        }
    }

    @Override
    void setupWithStatus(StatusViewData status, final StatusActionListener listener,
                         boolean mediaPreviewEnabled) {
        super.setupWithStatus(status, listener, mediaPreviewEnabled);
        reblogs.setText(status.getReblogsCount());
        favourites.setText(status.getFavouritesCount());
        setApplication(status.getApplication());
    }
}
