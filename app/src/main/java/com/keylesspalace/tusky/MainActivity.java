package com.keylesspalace.tusky;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements
        SwipeRefreshLayout.OnRefreshListener {

    private String domain = null;
    private String accessToken = null;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TimelineAdapter adapter;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        assert(domain != null);
        assert(accessToken != null);

        // Setup the SwipeRefreshLayout.
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Setup the RecyclerView.
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                this, layoutManager.getOrientation());
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.status_divider);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                TimelineAdapter adapter = (TimelineAdapter) view.getAdapter();
                String fromId = adapter.getItem(adapter.getItemCount() - 1).getId();
                sendFetchTimelineRequest(fromId);
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
        adapter = new TimelineAdapter();
        recyclerView.setAdapter(adapter);

        sendFetchTimelineRequest();
    }

    private Date parseDate(String dateTime) {
        Date date;
        String s = dateTime.replace("Z", "+00:00");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            date = format.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return date;
    }

    private CharSequence trimTrailingWhitespace(CharSequence s) {
        int i = s.length();
        do {
            i--;
        } while (i >= 0 && Character.isWhitespace(s.charAt(i)));
        return s.subSequence(0, i + 1);
    }

    private Spanned compatFromHtml(String html) {
        Spanned result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(html);
        }
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * all status contents do, so it should be trimmed. */
        return (Spanned) trimTrailingWhitespace(result);
    }

    private Status parseStatus(JSONObject object, boolean isReblog) throws JSONException {
        String id = object.getString("id");
        String content = object.getString("content");
        Date createdAt = parseDate(object.getString("created_at"));

        JSONObject account = object.getJSONObject("account");
        String displayName = account.getString("display_name");
        String username = account.getString("acct");
        String avatar = account.getString("avatar");

        Status reblog = null;
        /* This case shouldn't be hit after the first recursion at all. But if this method is
         * passed unusual data this check will prevent extra recursion */
        if (!isReblog) {
            JSONObject reblogObject = object.optJSONObject("reblog");
            if (reblogObject != null) {
                reblog = parseStatus(reblogObject, true);
            }
        }

        Status status;
        if (reblog != null) {
            status = reblog;
            status.setRebloggedByUsername(username);
        } else {
            Spanned contentPlus = compatFromHtml(content);
            status = new Status(id, displayName, username, contentPlus, avatar, createdAt);
        }
        return status;
    }

    private List<Status> parseStatuses(JSONArray array) throws JSONException {
        List<Status> statuses = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            statuses.add(parseStatus(object, false));
        }
        return statuses;
    }

    private void sendFetchTimelineRequest(final String fromId) {
        String endpoint = getString(R.string.endpoint_timelines_home);
        String url = "https://" + domain + endpoint;
        JsonArrayRequest request = new JsonArrayRequest(url,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    List<Status> statuses = null;
                    try {
                        statuses = parseStatuses(response);
                    } catch (JSONException e) {
                        onFetchTimelineFailure(e);
                    }
                    if (statuses != null) {
                        onFetchTimelineSuccess(statuses, fromId != null);
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    onFetchTimelineFailure(error);
                }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> parameters = new HashMap<>();
                parameters.put("max_id", fromId);
                return parameters;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void sendFetchTimelineRequest() {
        sendFetchTimelineRequest(null);
    }

    public void onFetchTimelineSuccess(List<Status> statuses, boolean added) {
        if (added) {
            adapter.addItems(statuses);
        } else {
            adapter.update(statuses);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    public void onFetchTimelineFailure(Exception exception) {
        Toast.makeText(this, R.string.error_fetching_timeline, Toast.LENGTH_SHORT).show();
        swipeRefreshLayout.setRefreshing(false);
    }

    public void onRefresh() {
        sendFetchTimelineRequest();
    }


    private void logOut() {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("domain");
        editor.remove("accessToken");
        editor.apply();
        Intent intent = new Intent(this, SplashActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout: {
                logOut();
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }
}
