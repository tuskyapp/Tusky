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

package com.keylesspalace.tusky;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.keylesspalace.tusky.adapter.SavedTootAdapter;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.SaveTootHelper;
import com.keylesspalace.tusky.util.ThemeUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class SavedTootActivity extends BaseActivity implements SavedTootAdapter.SavedTootAction {

    // dao
    private static TootDao tootDao = TuskyApplication.getDB().tootDao();

    private SaveTootHelper saveTootHelper;

    // ui
    private SavedTootAdapter adapter;
    private TextView noContent;

    private List<TootEntity> toots = new ArrayList<>();
    @Nullable private AsyncTask<?, ?, ?> asyncTask;

    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        saveTootHelper = new SaveTootHelper(tootDao, this);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                fetchToots();
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TimelineReceiver.Types.STATUS_COMPOSED);

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, intentFilter);

        setContentView(R.layout.activity_saved_toot);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(getString(R.string.title_saved_toot));
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        noContent = findViewById(R.id.no_content);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                this, layoutManager.getOrientation());
        Drawable drawable = ThemeUtils.getDrawable(this, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        adapter = new SavedTootAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchToots();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (asyncTask != null) asyncTask.cancel(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchToots() {
        asyncTask = new FetchPojosTask(this)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void setNoContent(int size) {
        if (size == 0) {
            noContent.setVisibility(View.VISIBLE);
        } else {
            noContent.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void delete(int position, TootEntity item) {

        saveTootHelper.deleteDraft(item);

        toots.remove(position);
        // update adapter
        if (adapter != null) {
            adapter.removeItem(position);
            setNoContent(toots.size());
        }
    }

    @Override
    public void click(int position, TootEntity item) {
        Intent intent = new ComposeActivity.IntentBuilder()
                .savedTootUid(item.getUid())
                .savedTootText(item.getText())
                .contentWarning(item.getContentWarning())
                .savedJsonUrls(item.getUrls())
                .inReplyToId(item.getInReplyToId())
                .repyingStatusAuthor(item.getInReplyToUsername())
                .replyingStatusContent(item.getInReplyToText())
                .replyVisibility(item.getVisibility())
                .build(this);
        startActivity(intent);
    }

    static final class FetchPojosTask extends AsyncTask<Void, Void, List<TootEntity>> {

        private final WeakReference<SavedTootActivity> activityRef;

        FetchPojosTask(SavedTootActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected List<TootEntity> doInBackground(Void... voids) {
            return tootDao.loadAll();
        }

        @Override
        protected void onPostExecute(List<TootEntity> pojos) {
            super.onPostExecute(pojos);
            SavedTootActivity activity = activityRef.get();
            if (activity == null) return;

            activity.toots.addAll(pojos);

            // set ui
            activity.setNoContent(pojos.size());
            List<TootEntity> toots = new ArrayList<>(pojos.size());
            toots.addAll(pojos);
            activity.adapter.setItems(toots);
            activity.adapter.notifyDataSetChanged();
        }
    }
}
