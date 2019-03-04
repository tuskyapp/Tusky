/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Tusky. If
 * not, see <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.keylesspalace.tusky.BuildConfig;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class OkHttpUtils {

    @NonNull
    public static OkHttpClient.Builder getCompatibleClientBuilder(@NonNull Context context) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean httpProxyEnabled = preferences.getBoolean("httpProxyEnabled", false);
        String httpServer = preferences.getString("httpProxyServer", "");
        int httpPort;
        try {
            httpPort = Integer.parseInt(preferences.getString("httpProxyPort", "-1"));
        } catch (NumberFormatException e) {
            // user has entered wrong port, fall back to no proxy
            httpPort = -1;
        }

        int cacheSize = 25*1024*1024; // 25 MiB

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(getUserAgentInterceptor())
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .cache(new Cache(context.getCacheDir(), cacheSize));

        if (httpProxyEnabled && !httpServer.isEmpty() && (httpPort > 0) && (httpPort < 65535)) {
            InetSocketAddress address = InetSocketAddress.createUnresolved(httpServer, httpPort);
            builder.proxy(new Proxy(Proxy.Type.HTTP, address));
        }

        return builder;
    }

    /**
     * Add a custom User-Agent that contains Tusky & Android Version to all requests
     * Example:
     * User-Agent: Tusky/1.1.2 Android/5.0.2
     */
    @NonNull
    private static Interceptor getUserAgentInterceptor() {
        return chain -> {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "Tusky/"+ BuildConfig.VERSION_NAME+" Android/"+Build.VERSION.RELEASE)
                    .build();
            return chain.proceed(requestWithUserAgent);
        };
    }

}


