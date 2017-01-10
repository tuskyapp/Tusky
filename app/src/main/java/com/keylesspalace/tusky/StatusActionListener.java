package com.keylesspalace.tusky;

import android.view.View;

public interface StatusActionListener {
    void onReblog(final boolean reblog, final int position);
    void onFavourite(final boolean favourite, final int position);
    void onMore(View view, final int position);
    void onViewMedia(String url, Status.MediaAttachment.Type type);
}
