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

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.keylesspalace.tusky.adapter.SearchResultsAdapter;
import com.keylesspalace.tusky.entity.SearchResults;
import com.keylesspalace.tusky.interfaces.LinkListener;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends BaseActivity implements SearchView.OnQueryTextListener,
        LinkListener {
    private static final String TAG = "SearchActivity"; // logging tag

    private ProgressBar progressBar;
    private TextView messageNoResults;
    private SearchResultsAdapter adapter;
    private String currentQuery;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        progressBar = findViewById(R.id.progress_bar);
        messageNoResults = findViewById(R.id.message_no_results);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
            bar.setDisplayShowTitleEnabled(false);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultsAdapter(this);
        recyclerView.setAdapter(adapter);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.search_toolbar, menu);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
                .getActionView();
        setupSearchView(searchView);

        if (currentQuery != null) {
            searchView.setQuery(currentQuery, false);
        }

        return true;
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

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public void onViewAccount(String id) {
        Intent intent = new Intent(this, AccountActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    @Override
    public void onViewTag(String tag) {
        Intent intent = new Intent(this, ViewTagActivity.class);
        intent.putExtra("hashtag", tag);
        startActivity(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            currentQuery = intent.getStringExtra(SearchManager.QUERY);
            search(currentQuery);
        }
    }

    private void setupSearchView(SearchView searchView) {
        searchView.setIconifiedByDefault(false);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
            searchView.setSearchableInfo(searchableInfo);
        }

        searchView.setOnQueryTextListener(this);
        searchView.requestFocus();

        searchView.setMaxWidth(Integer.MAX_VALUE);
    }

    private void search(String query) {
        clearResults();
        Callback<SearchResults> callback = new Callback<SearchResults>() {
            @Override
            public void onResponse(@NonNull Call<SearchResults> call, @NonNull Response<SearchResults> response) {
                if (response.isSuccessful()) {
                    SearchResults results = response.body();
                    if (results.accounts != null && results.accounts.length > 0 || results.hashtags != null && results.hashtags.length > 0) {
                        adapter.updateSearchResults(results);
                        hideFeedback();
                    } else {
                        displayNoResults();
                    }
                } else {
                    onSearchFailure();
                }
            }

            @Override
            public void onFailure(@NonNull Call<SearchResults> call, @NonNull Throwable t) {
                onSearchFailure();
            }
        };
        mastodonApi.search(query, false)
                .enqueue(callback);
    }

    private void onSearchFailure() {
        displayNoResults();
        Log.e(TAG, "Search request failed.");
    }

    private void clearResults() {
        adapter.updateSearchResults(null);
        progressBar.setVisibility(View.VISIBLE);
        messageNoResults.setVisibility(View.GONE);
    }

    private void displayNoResults() {
        progressBar.setVisibility(View.GONE);
        messageNoResults.setVisibility(View.VISIBLE);
    }

    private void hideFeedback() {
        progressBar.setVisibility(View.GONE);
        messageNoResults.setVisibility(View.GONE);
    }
}
