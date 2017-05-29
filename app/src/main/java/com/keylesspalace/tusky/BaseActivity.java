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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.entity.Session;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.json.StringWithEmoji;
import com.keylesspalace.tusky.json.StringWithEmojiTypeAdapter;
import com.keylesspalace.tusky.network.MastodonAPI;
import com.keylesspalace.tusky.network.TuskyApi;
import com.keylesspalace.tusky.util.OkHttpUtils;
import com.keylesspalace.tusky.util.PushNotificationClient;

import java.io.IOException;

import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity"; // logging tag

    public MastodonAPI mastodonAPI;
    public TuskyApi tuskyApi;
    protected PushNotificationClient pushNotificationClient;
    protected Dispatcher mastodonApiDispatcher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        redirectIfNotLoggedIn();
        createMastodonAPI();
        createTuskyApi();
        createPushNotificationClient();

        /* There isn't presently a way to globally change the theme of a whole application at
         * runtime, just individual activities. So, each activity has to set its theme before any
         * views are created. */
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("lightTheme", false)) {
            setTheme(R.style.AppTheme_Light);
        }
    }

    @Override
    protected void onDestroy() {
        if(mastodonApiDispatcher != null) mastodonApiDispatcher.cancelAll();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransitionExit();
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransitionEnter();
    }

    private void overridePendingTransitionEnter() {
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
    }

    private void overridePendingTransitionExit() {
        overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
    }

    protected SharedPreferences getPrivatePreferences() {
        return getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
    }

    protected String getAccessToken() {
        SharedPreferences preferences = getPrivatePreferences();
        return preferences.getString("accessToken", null);
    }

    protected boolean arePushNotificationsEnabled() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean("notificationsEnabled", true);
    }

    protected String getBaseUrl() {
        SharedPreferences preferences = getPrivatePreferences();
        return "https://" + preferences.getString("domain", null);
    }

    protected void createMastodonAPI() {
        mastodonApiDispatcher = new Dispatcher();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .registerTypeAdapter(StringWithEmoji.class, new StringWithEmojiTypeAdapter())
                .create();

        OkHttpClient okHttpClient = OkHttpUtils.getCompatibleClientBuilder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request originalRequest = chain.request();

                        Request.Builder builder = originalRequest.newBuilder();
                        String accessToken = getAccessToken();
                        if (accessToken != null) {
                            builder.header("Authorization", String.format("Bearer %s",
                                    accessToken));
                        }
                        Request newRequest = builder.build();

                        return chain.proceed(newRequest);
                    }
                })
                .dispatcher(mastodonApiDispatcher)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getBaseUrl())
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        mastodonAPI = retrofit.create(MastodonAPI.class);
    }

    protected void createTuskyApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + getString(R.string.tusky_api_url))
                .client(OkHttpUtils.getCompatibleClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        tuskyApi = retrofit.create(TuskyApi.class);
    }

    protected void createPushNotificationClient() {
        pushNotificationClient = new PushNotificationClient(getApplicationContext(),
                "ssl://" + getString(R.string.tusky_api_url) + ":8883");
    }

    protected void redirectIfNotLoggedIn() {
        SharedPreferences preferences = getPrivatePreferences();
        String domain = preferences.getString("domain", null);
        String accessToken = preferences.getString("accessToken", null);
        if (domain == null || accessToken == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        TypedValue value = new TypedValue();
        int color;
        if (getTheme().resolveAttribute(R.attr.toolbar_icon_tint, value, true)) {
            color = value.data;
        } else {
            color = Color.WHITE;
        }
        for (int i = 0; i < menu.size(); i++) {
            Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) {
                icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    protected void enablePushNotifications() {
        Callback<ResponseBody> callback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call,
                                   retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    pushNotificationClient.subscribeToTopic(getPushNotificationTopic());
                    pushNotificationClient.connect(BaseActivity.this);
                } else {
                    onEnablePushNotificationsFailure(response.message());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                onEnablePushNotificationsFailure(t.getMessage());
            }
        };
        String deviceToken = pushNotificationClient.getDeviceToken();
        Session session = new Session(getDomain(), getAccessToken(), deviceToken);
        tuskyApi.register(session)
                .enqueue(callback);
    }

    private void onEnablePushNotificationsFailure(String message) {
        Log.e(TAG, "Enabling push notifications failed. " + message);
    }

    protected void disablePushNotifications() {
        Callback<ResponseBody> callback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call,
                                   retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    pushNotificationClient.unsubscribeToTopic(getPushNotificationTopic());
                } else {
                    onDisablePushNotificationsFailure();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                onDisablePushNotificationsFailure();
            }
        };
        String deviceToken = pushNotificationClient.getDeviceToken();
        Session session = new Session(getDomain(), getAccessToken(), deviceToken);
        tuskyApi.unregister(session)
                .enqueue(callback);
    }

    private void onDisablePushNotificationsFailure() {
        Log.e(TAG, "Disabling push notifications failed.");
    }

    private String getPushNotificationTopic() {
        return String.format("%s/%s/#", getDomain(), getAccessToken());
    }

    private String getDomain() {
        return getPrivatePreferences()
                .getString("domain", null);
    }
}
