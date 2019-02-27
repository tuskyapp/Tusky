/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.snackbar.Snackbar;

import tech.bigfig.roma.adapter.ReportAdapter;
import tech.bigfig.roma.di.Injectable;
import tech.bigfig.roma.entity.Status;
import tech.bigfig.roma.network.MastodonApi;
import tech.bigfig.roma.util.HtmlUtils;
import tech.bigfig.roma.util.ThemeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportActivity extends BaseActivity implements Injectable {
    private static final String TAG = "ReportActivity"; // logging tag

    @Inject
    public MastodonApi mastodonApi;

    private View anyView; // what Snackbar will use to find the root view
    private ReportAdapter adapter;
    private boolean reportAlreadyInFlight;
    private String accountId;
    private EditText comment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        Intent intent = getIntent();
        accountId = intent.getStringExtra("account_id");
        String accountUsername = intent.getStringExtra("account_username");
        String statusId = intent.getStringExtra("status_id");
        String statusContent = intent.getStringExtra("status_content");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            String title = String.format(getString(R.string.report_username_format),
                    accountUsername);
            bar.setTitle(title);
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }
        anyView = toolbar;

        final RecyclerView recyclerView = findViewById(R.id.report_recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ReportAdapter();
        recyclerView.setAdapter(adapter);

        DividerItemDecoration divider = new DividerItemDecoration(
                this, layoutManager.getOrientation());
        recyclerView.addItemDecoration(divider);

        ReportAdapter.ReportStatus reportStatus = new ReportAdapter.ReportStatus(statusId,
                HtmlUtils.fromHtml(statusContent), true);
        adapter.addItem(reportStatus);

        comment = findViewById(R.id.report_comment);

        reportAlreadyInFlight = false;

        fetchRecentStatuses(accountId);
    }

    private void onClickSend() {
        if (reportAlreadyInFlight) {
            return;
        }

        String[] statusIds = adapter.getCheckedStatusIds();

        if (statusIds.length > 0) {
            reportAlreadyInFlight = true;
            sendReport(accountId, statusIds, comment.getText().toString());
        } else {
            comment.setError(getString(R.string.error_report_too_few_statuses));
        }
    }

    private void sendReport(final String accountId, final String[] statusIds,
                            final String comment) {
        Callback<ResponseBody> callback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    onSendSuccess();
                } else {
                    onSendFailure(accountId, statusIds, comment);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                onSendFailure(accountId, statusIds, comment);
            }
        };
        mastodonApi.report(accountId, Arrays.asList(statusIds), comment)
                .enqueue(callback);
    }

    private void onSendSuccess() {
        Snackbar bar = Snackbar.make(anyView, getString(R.string.confirmation_reported), Snackbar.LENGTH_SHORT);
        bar.show();
        finish();
    }

    private void onSendFailure(final String accountId, final String[] statusIds,
                               final String comment) {
        Snackbar.make(anyView, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendReport(accountId, statusIds, comment);
                    }
                })
                .show();
        reportAlreadyInFlight = false;
    }

    private void fetchRecentStatuses(String accountId) {
        Callback<List<Status>> callback = new Callback<List<Status>>() {
            @Override
            public void onResponse(Call<List<Status>> call, Response<List<Status>> response) {
                if (!response.isSuccessful()) {
                    onFetchStatusesFailure(new Exception(response.message()));
                    return;
                }
                List<Status> statusList = response.body();
                List<ReportAdapter.ReportStatus> itemList = new ArrayList<>();
                for (Status status : statusList) {
                    if (status.getReblog() == null) {
                        ReportAdapter.ReportStatus item = new ReportAdapter.ReportStatus(
                                status.getId(), status.getContent(), false);
                        itemList.add(item);
                    }
                }
                adapter.addItems(itemList);
            }

            @Override
            public void onFailure(Call<List<Status>> call, Throwable t) {
                onFetchStatusesFailure((Exception) t);
            }
        };
        mastodonApi.accountStatuses(accountId, null, null, null, null, null, null)
                .enqueue(callback);
    }

    private void onFetchStatusesFailure(Exception exception) {
        Log.e(TAG, "Failed to fetch recent statuses to report. " + exception.getMessage());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.report_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.action_report: {
                onClickSend();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
