package com.keylesspalace.tusky;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimelineFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener, StatusActionListener, FooterActionListener {

    public enum Kind {
        HOME,
        MENTIONS,
        PUBLIC,
    }

    private String domain = null;
    private String accessToken = null;
    private String userAccountId = null;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TimelineAdapter adapter;
    private Kind kind;
    private LinearLayoutManager layoutManager;
    private EndlessOnScrollListener scrollListener;
    private TabLayout.OnTabSelectedListener onTabSelectedListener;

    public static TimelineFragment newInstance(Kind kind) {
        TimelineFragment fragment = new TimelineFragment();
        Bundle arguments = new Bundle();
        arguments.putString("kind", kind.name());
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
             Bundle savedInstanceState) {

        kind = Kind.valueOf(getArguments().getString("kind"));

        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        Context context = getContext();
        SharedPreferences preferences = context.getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        assert(domain != null);
        assert(accessToken != null);

        // Setup the SwipeRefreshLayout.
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Setup the RecyclerView.
        recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.status_divider);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                TimelineAdapter adapter = (TimelineAdapter) view.getAdapter();
                Status status = adapter.getItem(adapter.getItemCount() - 2);
                if (status != null) {
                    sendFetchTimelineRequest(status.getId());
                } else {
                    sendFetchTimelineRequest();
                }

            }
        };
        recyclerView.addOnScrollListener(scrollListener);
        adapter = new TimelineAdapter(this, this);
        recyclerView.setAdapter(adapter);

        TabLayout layout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
        onTabSelectedListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {}

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                jumpToTop();
            }
        };
        layout.addOnTabSelectedListener(onTabSelectedListener);

        sendUserInfoRequest();
        sendFetchTimelineRequest();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        TabLayout tabLayout = (TabLayout) getActivity().findViewById(R.id.tab_layout);
        tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        super.onDestroyView();
    }

    private void jumpToTop() {
        layoutManager.scrollToPositionWithOffset(0, 0);
        scrollListener.reset();
    }

    private void sendUserInfoRequest() {
        sendRequest(Request.Method.GET, getString(R.string.endpoint_verify_credentials), null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            userAccountId = response.getString("id");
                        } catch (JSONException e) {
                            //TODO: Help
                            assert(false);
                        }
                    }
                });
    }

    private void sendFetchTimelineRequest(final String fromId) {
        String endpoint;
        switch (kind) {
            default:
            case HOME: {
                endpoint = getString(R.string.endpoint_timelines_home);
                break;
            }
            case MENTIONS: {
                endpoint = getString(R.string.endpoint_timelines_mentions);
                break;
            }
            case PUBLIC: {
                endpoint = getString(R.string.endpoint_timelines_public);
                break;
            }
        }
        String url = "https://" + domain + endpoint;
        if (fromId != null) {
            url += "?max_id=" + fromId;
        }
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        List<Status> statuses = null;
                        try {
                            statuses = Status.parse(response);
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
        };
        VolleySingleton.getInstance(getContext()).addToRequestQueue(request);
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
        showFetchTimelineRetry(false);
        swipeRefreshLayout.setRefreshing(false);
    }

    public void onFetchTimelineFailure(Exception exception) {
        showFetchTimelineRetry(true);
        swipeRefreshLayout.setRefreshing(false);
    }

    private void showFetchTimelineRetry(boolean show) {
        RecyclerView.ViewHolder viewHolder =
                recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
        if (viewHolder != null) {
            TimelineAdapter.FooterViewHolder holder = (TimelineAdapter.FooterViewHolder) viewHolder;
            holder.showRetry(show);
        }
    }

    public void onRefresh() {
        sendFetchTimelineRequest();
    }

    private void sendRequest(
            int method, String endpoint, JSONObject parameters,
            @Nullable Response.Listener<JSONObject> responseListener) {
        if (responseListener == null) {
            // Use a dummy listener if one wasn't specified so the request can be constructed.
            responseListener = new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {}
            };
        }
        String url = "https://" + domain + endpoint;
        JsonObjectRequest request = new JsonObjectRequest(
                method, url, parameters, responseListener,
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        System.err.println(error.getMessage());
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(getContext()).addToRequestQueue(request);
    }

    private void postRequest(String endpoint) {
        sendRequest(Request.Method.POST, endpoint, null, null);
    }

    public void onReblog(final boolean reblog, final int position) {
        final Status status = adapter.getItem(position);
        String id = status.getId();
        String endpoint;
        if (reblog) {
            endpoint = String.format(getString(R.string.endpoint_reblog), id);
        } else {
            endpoint = String.format(getString(R.string.endpoint_unreblog), id);
        }
        sendRequest(Request.Method.POST, endpoint, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        status.setReblogged(reblog);
                        adapter.notifyItemChanged(position);
                    }
                });
    }

    public void onFavourite(final boolean favourite, final int position) {
        final Status status = adapter.getItem(position);
        String id = status.getId();
        String endpoint;
        if (favourite) {
            endpoint = String.format(getString(R.string.endpoint_favourite), id);
        } else {
            endpoint = String.format(getString(R.string.endpoint_unfavourite), id);
        }
        sendRequest(Request.Method.POST, endpoint, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                status.setFavourited(favourite);
                adapter.notifyItemChanged(position);
            }
        });
    }

    private void follow(String id) {
        String endpoint = String.format(getString(R.string.endpoint_follow), id);
        postRequest(endpoint);
    }

    private void block(String id) {
        String endpoint = String.format(getString(R.string.endpoint_block), id);
        postRequest(endpoint);
    }

    private void delete(String id) {
        String endpoint = String.format(getString(R.string.endpoint_delete), id);
        sendRequest(Request.Method.DELETE, endpoint, null, null);
    }

    public void onMore(View view, final int position) {
        Status status = adapter.getItem(position);
        final String id = status.getId();
        final String accountId = status.getAccountId();
        PopupMenu popup = new PopupMenu(getContext(), view);
        // Give a different menu depending on whether this is the user's own toot or not.
        if (userAccountId == null || !userAccountId.equals(accountId)) {
            popup.inflate(R.menu.status_more);
        } else {
            popup.inflate(R.menu.status_more_for_user);
        }
        popup.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.status_follow: {
                                follow(accountId);
                                return true;
                            }
                            case R.id.status_block: {
                                block(accountId);
                                return true;
                            }
                            case R.id.status_delete: {
                                delete(id);
                                adapter.removeItem(position);
                                return true;
                            }
                        }
                        return false;
                    }
                });
        popup.show();
    }

    public void onViewMedia(String url, Status.MediaAttachment.Type type) {
        switch (type) {
            case IMAGE: {
                Fragment newFragment = ViewMediaFragment.newInstance(url);
                FragmentManager manager = getFragmentManager();
                manager.beginTransaction()
                        .add(R.id.overlay_fragment_container, newFragment)
                        .addToBackStack(null)
                        .commit();
                break;
            }
            case VIDEO: {
                Intent intent = new Intent(getContext(), ViewVideoActivity.class);
                intent.putExtra("url", url);
                startActivity(intent);
                break;
            }
        }
    }

    public void onLoadMore() {
        Status status = adapter.getItem(adapter.getItemCount() - 2);
        if (status != null) {
            sendFetchTimelineRequest(status.getId());
        } else {
            sendFetchTimelineRequest();
        }
    }
}
