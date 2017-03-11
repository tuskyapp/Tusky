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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.arlib.floatingsearchview.suggestions.SearchSuggestionsAdapter;
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
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
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader;
import com.mikepenz.materialdrawer.util.DrawerImageLoader;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.Stack;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity"; // logging tag and Volley request tag

    private AlarmManager alarmManager;
    private PendingIntent serviceAlarmIntent;
    private boolean notificationServiceEnabled;
    private String loggedInAccountId;
    private String loggedInAccountUsername;
    Stack<Integer> pageHistory = new Stack<Integer>();
    private ViewPager viewPager;
    private AccountHeader headerResult;
    private Drawer drawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fetch user info while we're doing other things.
        fetchUserInfo();

        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        FloatingActionButton floatingBtn = (FloatingActionButton) findViewById(R.id.floating_btn);
        floatingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), ComposeActivity.class);
                startActivity(intent);
            }
        });

        final FloatingSearchView searchView = (FloatingSearchView) findViewById(R.id.floating_search_view);

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
                                if (notificationServiceEnabled) {
                                    alarmManager.cancel(serviceAlarmIntent);
                                }
                                SharedPreferences preferences = getSharedPreferences(
                                        getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.remove("domain");
                                editor.remove("accessToken");
                                editor.apply();
                                Intent intent = new Intent(MainActivity.this, SplashActivity.class);
                                startActivity(intent);
                                finish();
                            }
                        }

                        return false;
                    }
                })
                .build();

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
                        searchView.swapSuggestions(response.body());
                        searchView.hideProgress();
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

        // Setup the tabs and timeline pager.
        TimelinePagerAdapter adapter = new TimelinePagerAdapter(getSupportFragmentManager());
        String[] pageTitles = {
                getString(R.string.title_home),
                getString(R.string.title_notifications),
                getString(R.string.title_public)
        };
        adapter.setPageTitles(pageTitles);
        viewPager = (ViewPager) findViewById(R.id.pager);
        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
        viewPager.setPageMarginDrawable(pageMarginDrawable);
        viewPager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());

                if (pageHistory.empty()) {
                    pageHistory.push(0);
                }

                if (pageHistory.contains(tab.getPosition())) {
                    pageHistory.remove(pageHistory.indexOf(tab.getPosition()));
                }

                pageHistory.push(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        // Retrieve notification update preference.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        notificationServiceEnabled = preferences.getBoolean("pullNotifications", true);
        String minutesString = preferences.getString("pullNotificationCheckInterval", "15");
        long notificationCheckInterval = 60 * 1000 * Integer.valueOf(minutesString);
        // Start up the PullNotificationsService.
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, PullNotificationService.class);
        final int SERVICE_REQUEST_CODE = 8574603; // This number is arbitrary.
        serviceAlarmIntent = PendingIntent.getService(this, SERVICE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (notificationServiceEnabled) {
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime(), notificationCheckInterval, serviceAlarmIntent);
        } else {
            alarmManager.cancel(serviceAlarmIntent);
        }
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
        } else if(pageHistory.empty()) {
            super.onBackPressed();
        } else {
            pageHistory.pop();
            viewPager.setCurrentItem(pageHistory.lastElement());
        }
    }
}