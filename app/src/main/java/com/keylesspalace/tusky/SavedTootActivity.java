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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.adapter.SavedTootAdapter;
import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.StatusComposedEvent;
import com.keylesspalace.tusky.components.compose.ComposeActivity;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.util.SaveTootHelper;
import com.keylesspalace.tusky.view.BackgroundMessageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;

import static com.keylesspalace.tusky.components.compose.ComposeActivity.ComposeOptions;
import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;

public final class SavedTootActivity extends BaseActivity implements SavedTootAdapter.SavedTootAction,
        Injectable {

    // ui
    private SavedTootAdapter adapter;
    private BackgroundMessageView errorMessageView;

    private List<TootEntity> toots = new ArrayList<>();
    @Nullable
    private AsyncTask<?, ?, ?> asyncTask;

    @Inject
    EventHub eventHub;
    @Inject
    AppDatabase database;
    @Inject
    SaveTootHelper saveTootHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        eventHub.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .ofType(StatusComposedEvent.class)
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe((__) -> this.fetchToots());

        setContentView(R.layout.activity_saved_toot);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(getString(R.string.title_saved_toot));
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        errorMessageView = findViewById(R.id.errorMessageView);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                this, layoutManager.getOrientation());
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
        asyncTask = new FetchPojosTask(this, database.tootDao())
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void setNoContent(int size) {
        if (size == 0) {
            errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_saved_status, null);
            errorMessageView.setVisibility(View.VISIBLE);
        } else {
            errorMessageView.setVisibility(View.GONE);
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
        Gson gson = new Gson();
        Type stringListType = new TypeToken<List<String>>() {}.getType();
        List<String> jsonUrls = gson.fromJson(item.getUrls(), stringListType);
        List<String> descriptions = gson.fromJson(item.getDescriptions(), stringListType);

        ComposeOptions composeOptions = new ComposeOptions(
                item.getUid(),
                item.getText(),
                jsonUrls,
                descriptions,
                /*mentionedUsernames*/null,
                item.getInReplyToId(),
                /*replyVisibility*/null,
                item.getVisibility(),
                item.getContentWarning(),
                item.getInReplyToUsername(),
                item.getInReplyToText(),
                /*mediaAttachments*/null,
                /*scheduledAt*/null,
                /*sensitive*/null,
                /*poll*/null
        );
        Intent intent = ComposeActivity.startIntent(this, composeOptions);
        startActivity(intent);
    }

    static final class FetchPojosTask extends AsyncTask<Void, Void, List<TootEntity>> {

        private final WeakReference<SavedTootActivity> activityRef;
        private final TootDao tootDao;

        FetchPojosTask(SavedTootActivity activity, TootDao tootDao) {
            this.activityRef = new WeakReference<>(activity);
            this.tootDao = tootDao;
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

            activity.toots.clear();
            activity.toots.addAll(pojos);

            // set ui
            activity.setNoContent(pojos.size());
            activity.adapter.setItems(activity.toots);
            activity.adapter.notifyDataSetChanged();
        }
    }
}
