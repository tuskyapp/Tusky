/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.arlib.floatingsearchview.suggestions.SearchSuggestionsAdapter;
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.google.firebase.iid.FirebaseInstanceId;
import com.keylesspalace.tusky.entity.Account;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader;
import com.mikepenz.materialdrawer.util.DrawerImageLoader;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity"; // logging tag and Volley request tag

    private String loggedInAccountId;
    private String loggedInAccountUsername;
    private Stack<Integer> pageHistory;
    private AccountHeader headerResult;
    private Drawer drawer;

    @BindView(R.id.floating_search_view) FloatingSearchView searchView;
    @BindView(R.id.floating_btn) FloatingActionButton floatingBtn;
    @BindView(R.id.tab_layout) TabLayout tabLayout;
    @BindView(R.id.pager) ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pageHistory = new Stack<>();
        if (savedInstanceState != null) {
            List<Integer> restoredHistory = savedInstanceState.getIntegerArrayList("pageHistory");
            if (restoredHistory != null) {
                pageHistory.addAll(restoredHistory);
            }
        }

        ButterKnife.bind(this);

        // Fetch user info while we're doing other things.
        fetchUserInfo();

        floatingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), ComposeActivity.class);
                startActivity(intent);
            }
        });

        setupDrawer();
        setupSearchView();

        // Setup the tabs and timeline pager.
        TimelinePagerAdapter adapter = new TimelinePagerAdapter(getSupportFragmentManager());
        String[] pageTitles = {
                getString(R.string.title_home),
                getString(R.string.title_notifications),
                getString(R.string.title_public)
        };
        adapter.setPageTitles(pageTitles);

        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
        viewPager.setPageMarginDrawable(pageMarginDrawable);
        viewPager.setAdapter(adapter);

        tabLayout.setupWithViewPager(viewPager);

        tabLayout.getTabAt(0).setIcon(R.drawable.ic_home_24dp);
        tabLayout.getTabAt(1).setIcon(R.drawable.ic_notifications_24dp);
        tabLayout.getTabAt(2).setIcon(R.drawable.ic_public_24dp);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());

                if (pageHistory.isEmpty()) {
                    pageHistory.push(0);
                }

                if (pageHistory.contains(tab.getPosition())) {
                    pageHistory.remove(pageHistory.indexOf(tab.getPosition()));
                }

                pageHistory.push(tab.getPosition());
                tintTab(tab, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                tintTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        Intent intent = getIntent();

        if (intent != null) {
            int tabPosition = intent.getIntExtra("tab_position", 0);

            if (tabPosition != 0) {
                tabLayout.getTabAt(tabPosition).select();
                tintTab(tabLayout.getTabAt(tabPosition), true);
            } else {
                tintTab(tabLayout.getTabAt(0), true);
            }
        } else {
            tintTab(tabLayout.getTabAt(0), true);
        }

        // Setup push notifications
        tuskyAPI.register(getBaseUrl(), getAccessToken(), FirebaseInstanceId.getInstance().getToken()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences notificationPreferences = getApplicationContext().getSharedPreferences("Notifications", MODE_PRIVATE);
        SharedPreferences.Editor editor = notificationPreferences.edit();
        editor.putString("current", "[]");
        editor.apply();

        ((NotificationManager) (getSystemService(NOTIFICATION_SERVICE))).cancel(MyFirebaseMessagingService.NOTIFY_ID);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        ArrayList<Integer> pageHistoryList = new ArrayList<>();
        pageHistoryList.addAll(pageHistory);
        outState.putIntegerArrayList("pageHistory", pageHistoryList);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    private void tintTab(TabLayout.Tab tab, boolean tinted) {
        tab.getIcon().setColorFilter(ContextCompat.getColor(this, tinted ? R.color.color_accent_dark : R.color.toolbar_icon_dark), PorterDuff.Mode.SRC_IN);
    }

    private void setupDrawer() {
        headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withSelectionListEnabledForSingleProfile(false)
                .withDividerBelowHeader(false)
                .withCompactStyle(true)
                .build();

        DrawerImageLoader.init(new AbstractDrawerImageLoader() {
            @Override
            public void set(ImageView imageView, Uri uri, Drawable placeholder) {
                Picasso.with(imageView.getContext()).load(uri).placeholder(placeholder).into(imageView);
            }

            @Override
            public void cancel(ImageView imageView) {
                Picasso.with(imageView.getContext()).cancelRequest(imageView);
            }
        });

        drawer = new DrawerBuilder()
                .withActivity(this)
                //.withToolbar(toolbar)
                .withAccountHeader(headerResult)
                .withHasStableIds(true)
                .withSelectedItem(-1)
                .addDrawerItems(
                        new PrimaryDrawerItem().withIdentifier(0).withName(R.string.action_view_profile).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_person),
                        new PrimaryDrawerItem().withIdentifier(1).withName(getString(R.string.action_view_favourites)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_star),
                        new PrimaryDrawerItem().withIdentifier(2).withName(getString(R.string.action_view_blocks)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_block),
                        new DividerDrawerItem(),
                        new SecondaryDrawerItem().withIdentifier(3).withName(getString(R.string.action_view_preferences)).withSelectable(false),
                        new SecondaryDrawerItem().withIdentifier(4).withName(getString(R.string.action_logout)).withSelectable(false)
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            long drawerItemIdentifier = drawerItem.getIdentifier();

                            if (drawerItemIdentifier == 0) {
                                Intent intent = new Intent(MainActivity.this, AccountActivity.class);
                                intent.putExtra("id", loggedInAccountId);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 1) {
                                Intent intent = new Intent(MainActivity.this, FavouritesActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 2) {
                                Intent intent = new Intent(MainActivity.this, BlocksActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 3) {
                                Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 4) {
                                logout();
                            }
                        }

                        return false;
                    }
                })
                .build();
    }

    private void logout() {
        tuskyAPI.unregister(getBaseUrl(), getAccessToken()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

            }
        });

        SharedPreferences preferences = getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("domain");
        editor.remove("accessToken");
        editor.apply();

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void setupSearchView() {
        searchView.attachNavigationDrawerToMenuButton(drawer.getDrawerLayout());

        searchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
            @Override
            public void onSearchTextChanged(String oldQuery, String newQuery) {
                if (!oldQuery.equals("") && newQuery.equals("")) {
                    searchView.clearSuggestions();
                    return;
                }

                if (newQuery.length() < 3) {
                    return;
                }

                searchView.showProgress();

                mastodonAPI.searchAccounts(newQuery, false, 5).enqueue(new Callback<List<Account>>() {
                    @Override
                    public void onResponse(Call<List<Account>> call, Response<List<Account>> response) {
                        if (response.isSuccessful()) {
                            searchView.swapSuggestions(response.body());
                            searchView.hideProgress();
                        } else {
                            searchView.hideProgress();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Account>> call, Throwable t) {
                        searchView.hideProgress();
                    }
                });
            }
        });

        searchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
            @Override
            public void onSuggestionClicked(SearchSuggestion searchSuggestion) {
                Account accountSuggestion = (Account) searchSuggestion;
                Intent intent = new Intent(MainActivity.this, AccountActivity.class);
                intent.putExtra("id", accountSuggestion.id);
                startActivity(intent);
            }

            @Override
            public void onSearchAction(String currentQuery) {

            }
        });

        searchView.setOnBindSuggestionCallback(new SearchSuggestionsAdapter.OnBindSuggestionCallback() {
            @Override
            public void onBindSuggestion(View suggestionView, ImageView leftIcon, TextView textView, SearchSuggestion item, int itemPosition) {
                Account accountSuggestion = ((Account) item);

                Picasso.with(MainActivity.this)
                        .load(accountSuggestion.avatar)
                        .placeholder(R.drawable.avatar_default)
                        .into(leftIcon);

                String searchStr = accountSuggestion.getDisplayName() + " " + accountSuggestion.username;
                final SpannableStringBuilder str = new SpannableStringBuilder(searchStr);

                str.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, accountSuggestion.getDisplayName().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                textView.setText(str);
                textView.setMaxLines(1);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            }
        });
    }

    private void fetchUserInfo() {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        final String domain = preferences.getString("domain", null);
        String id = preferences.getString("loggedInAccountId", null);
        String username = preferences.getString("loggedInAccountUsername", null);

        if (id != null && username != null) {
            loggedInAccountId = id;
            loggedInAccountUsername = username;
        }

        mastodonAPI.accountVerifyCredentials().enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, retrofit2.Response<Account> response) {
                if (!response.isSuccessful()) {
                    onFetchUserInfoFailure(new Exception(response.message()));
                    return;
                }

                Account me = response.body();
                ImageView background = headerResult.getHeaderBackgroundView();

                Picasso.with(MainActivity.this)
                        .load(me.header)
                        .placeholder(R.drawable.account_header_missing)
                        .resize(background.getWidth(), background.getHeight())
                        .centerCrop()
                        .into(background);

                headerResult.addProfiles(
                        new ProfileDrawerItem()
                                .withName(me.getDisplayName())
                                .withEmail(String.format("%s@%s", me.username, domain))
                                .withIcon(me.avatar)
                );

                onFetchUserInfoSuccess(me.id, me.username);
            }

            @Override
            public void onFailure(Call<Account> call, Throwable t) {
                onFetchUserInfoFailure((Exception) t);
            }
        });
    }

    private void onFetchUserInfoSuccess(String id, String username) {
        loggedInAccountId = id;
        loggedInAccountUsername = username;
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("loggedInAccountId", loggedInAccountId);
        editor.putString("loggedInAccountUsername", loggedInAccountUsername);
        editor.apply();
    }

    private void onFetchUserInfoFailure(Exception exception) {
        Log.e(TAG, "Failed to fetch user info. " + exception.getMessage());
    }

    @Override
    public void onBackPressed() {
        if(drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else if(pageHistory.size() < 2) {
            super.onBackPressed();
        } else {
            pageHistory.pop();
            viewPager.setCurrentItem(pageHistory.peek());
        }
    }
}