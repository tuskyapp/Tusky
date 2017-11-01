package com.keylesspalace.tusky.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.keylesspalace.tusky.R;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by charlag on 31/10/17.
 */

public final class AuthInterceptor implements Interceptor, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TOKEN_KEY = "accessToken";

    @Nullable
    private String token;

    public AuthInterceptor(Context context) {
        SharedPreferences preferences  = context.getSharedPreferences(
                context.getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        token = preferences.getString(TOKEN_KEY, null);
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();

        Request.Builder builder = originalRequest.newBuilder();
        if (token != null) {
            builder.header("Authorization", String.format("Bearer %s", token));
        }
        Request newRequest = builder.build();

        return chain.proceed(newRequest);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(TOKEN_KEY)) {
            token = sharedPreferences.getString(TOKEN_KEY, null);
        }
    }
}
