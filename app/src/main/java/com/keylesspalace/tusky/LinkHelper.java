package com.keylesspalace.tusky;

import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Status;

class LinkHelper {
    static void setClickableText(TextView view, Spanned content,
                                 @Nullable Status.Mention[] mentions,
                                 final LinkListener listener) {
        SpannableStringBuilder builder = new SpannableStringBuilder(content);
        boolean useCustomTabs = PreferenceManager.getDefaultSharedPreferences(view.getContext())
                .getBoolean("customTabs", true);
        URLSpan[] urlSpans = content.getSpans(0, content.length(), URLSpan.class);
        for (URLSpan span : urlSpans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            int flags = builder.getSpanFlags(span);
            CharSequence text = builder.subSequence(start, end);
            if (text.charAt(0) == '#') {
                final String tag = text.subSequence(1, text.length()).toString();
                ClickableSpan newSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        listener.onViewTag(tag);
                    }
                };
                builder.removeSpan(span);
                builder.setSpan(newSpan, start, end, flags);
            } else if (text.charAt(0) == '@' && mentions != null) {
                final String accountUsername = text.subSequence(1, text.length()).toString();
                String id = null;
                for (Status.Mention mention : mentions) {
                    if (mention.localUsername.equals(accountUsername)) {
                        id = mention.id;
                    }
                }
                if (id != null) {
                    final String accountId = id;
                    ClickableSpan newSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            listener.onViewAccount(accountId);
                        }
                    };
                    builder.removeSpan(span);
                    builder.setSpan(newSpan, start, end, flags);
                }
            } else if (useCustomTabs) {
                ClickableSpan newSpan = new CustomTabURLSpan(span.getURL());
                builder.removeSpan(span);
                builder.setSpan(newSpan, start, end, flags);
            }
        }
        view.setText(builder);
        view.setLinksClickable(true);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
