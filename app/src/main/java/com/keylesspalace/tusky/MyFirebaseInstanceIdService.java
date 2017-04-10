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
 * see <http://www.gnu.org/licenses>.
 *
 * If you modify this Program, or any covered work, by linking or combining it with Firebase Cloud
 * Messaging and Firebase Crash Reporting (or a modified version of those libraries), containing
 * parts covered by the Google APIs Terms of Service, the licensors of this Program grant you
 * additional permission to convey the resulting work. */

package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MyFirebaseInstanceIdService extends FirebaseInstanceIdService {
    private static final String TAG = "MyFirebaseInstanceIdService";

    private TuskyAPI tuskyAPI;

    protected void createTuskyAPI() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getString(R.string.tusky_api_url))
                .build();

        tuskyAPI = retrofit.create(TuskyAPI.class);
    }

    @Override
    public void onTokenRefresh() {
        createTuskyAPI();

        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        SharedPreferences preferences = getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String accessToken = preferences.getString("accessToken", null);
        String domain = preferences.getString("domain", null);

        if (accessToken != null && domain != null) {
            tuskyAPI.unregister("https://" + domain, accessToken).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    Log.d(TAG, response.message());
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d(TAG, t.getMessage());
                }
            });
            tuskyAPI.register("https://" + domain, accessToken, refreshedToken).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    Log.d(TAG, response.message());
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d(TAG, t.getMessage());
                }
            });
        }
    }
}
