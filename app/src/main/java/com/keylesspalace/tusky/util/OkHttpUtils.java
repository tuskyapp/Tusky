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
import androidx.annotation.NonNull;
import android.util.Log;

import com.keylesspalace.tusky.BuildConfig;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class OkHttpUtils {
    private static final String TAG = "OkHttpUtils"; // logging tag

    /**
     * Makes a Builder with the maximum range of TLS versions and cipher suites enabled.
     * <p>
     * It first tries the "approved" list of cipher suites given in OkHttp (the default in
     * ConnectionSpec.MODERN_TLS) and if that doesn't work falls back to the set of ALL enabled,
     * then falls back to plain http.
     * <p>
     * API level 24 has a regression in elliptic curves where it only supports secp256r1, so this
     * first tries a fallback without elliptic curves at all, and then tries them after.
     * <p>
     * TLS 1.1 and 1.2 have to be manually enabled on API levels 16-20.
     */
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

        ConnectionSpec fallback = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .allEnabledCipherSuites()
                .supportsTlsExtensions(true)
                .build();

        List<ConnectionSpec> specList = new ArrayList<>();
        specList.add(ConnectionSpec.MODERN_TLS);
        addNougatFixConnectionSpec(specList);
        specList.add(fallback);
        specList.add(ConnectionSpec.CLEARTEXT);

        int cacheSize = 25*1024*1024; // 25 MiB

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(getUserAgentInterceptor())
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .cache(new Cache(context.getCacheDir(), cacheSize))
                .connectionSpecs(specList);

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

    /**
     * Android version Nougat has a regression where elliptic curve cipher suites are supported, but
     * only the curve secp256r1 is allowed. So, first it's best to just disable all elliptic
     * ciphers, try the connection, and fall back to the all cipher suites enabled list after.
     */
    private static void addNougatFixConnectionSpec(List<ConnectionSpec> specList) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.N) {
            return;
        }
        SSLSocketFactory socketFactory;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }

            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, null);
            socketFactory = sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException|KeyStoreException|KeyManagementException e) {
            Log.e(TAG, "Failed obtaining the SSL socket factory.");
            return;
        }
        String[] cipherSuites = socketFactory.getDefaultCipherSuites();
        ArrayList<String> allowedList = new ArrayList<>();
        for (String suite : cipherSuites) {
            if (!suite.contains("ECDH")) {
                allowedList.add(suite);
            }
        }
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .cipherSuites(allowedList.toArray(new String[0]))
                .supportsTlsExtensions(true)
                .build();
        specList.add(spec);
    }

}
