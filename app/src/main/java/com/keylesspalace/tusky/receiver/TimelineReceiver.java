package com.keylesspalace.tusky.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;

import com.keylesspalace.tusky.fragment.TimelineFragment;
import com.keylesspalace.tusky.interfaces.AdapterItemRemover;

public class TimelineReceiver extends BroadcastReceiver {
    public static final class Types {
        public static final String UNFOLLOW_ACCOUNT = "UNFOLLOW_ACCOUNT";
        public static final String BLOCK_ACCOUNT = "BLOCK_ACCOUNT";
        public static final String MUTE_ACCOUNT = "MUTE_ACCOUNT";
        public static final String STATUS_COMPOSED = "STATUS_COMPOSED";
    }

    AdapterItemRemover adapter;
    SwipeRefreshLayout.OnRefreshListener refreshListener;

    public TimelineReceiver(AdapterItemRemover adapter) {
        super();
        this.adapter = adapter;
    }

    public TimelineReceiver(AdapterItemRemover adapter,
                            SwipeRefreshLayout.OnRefreshListener refreshListener) {
        super();
        this.adapter = adapter;
        this.refreshListener = refreshListener;
    }

    @Override
    public void onReceive(Context context, final Intent intent) {
        switch (intent.getAction()) {
            case Types.STATUS_COMPOSED: {
                if (refreshListener != null) {
                    refreshListener.onRefresh();
                }
                break;
            }
            default: {
                String id = intent.getStringExtra("id");
                adapter.removeAllByAccountId(id);
                break;
            }
        }
    }

    public static IntentFilter getFilter(@Nullable TimelineFragment.Kind kind) {
        IntentFilter intentFilter = new IntentFilter();
        if (kind == TimelineFragment.Kind.HOME) {
            intentFilter.addAction(Types.UNFOLLOW_ACCOUNT);
        }
        intentFilter.addAction(Types.BLOCK_ACCOUNT);
        intentFilter.addAction(Types.MUTE_ACCOUNT);
        if (kind == null
                || kind == TimelineFragment.Kind.HOME
                || kind == TimelineFragment.Kind.PUBLIC_FEDERATED
                || kind == TimelineFragment.Kind.PUBLIC_LOCAL) {
            intentFilter.addAction(Types.STATUS_COMPOSED);
        }

        return intentFilter;
    }
}
