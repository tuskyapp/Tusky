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

import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.keylesspalace.tusky.BuildConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpUtils {
    static final String TAG = "OkHttpUtils"; // logging tag

    /**
     * Makes a Builder with the maximum range of TLS versions and cipher suites enabled.
     *
     * It first tries the "approved" list of cipher suites given in OkHttp (the default in
     * ConnectionSpec.MODERN_TLS) and if that doesn't work falls back to the set of ALL enabled,
     * then falls back to plain http.
     *
     * API level 24 has a regression in elliptic curves where it only supports secp256r1, so this
     * first tries a fallback without elliptic curves at all, and then tries them after.
     *
     * TLS 1.1 and 1.2 have to be manually enabled on API levels 16-20.
     */
    @NonNull
    public static OkHttpClient.Builder getCompatibleClientBuilder() {
        ConnectionSpec fallback = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .allEnabledCipherSuites()
                .supportsTlsExtensions(true)
                .build();

        List<ConnectionSpec> specList = new ArrayList<>();
        specList.add(ConnectionSpec.MODERN_TLS);
        addNougatFixConnectionSpec(specList);
        specList.add(fallback);
        specList.add(ConnectionSpec.CLEARTEXT);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(getUserAgentInterceptor())
                .connectionSpecs(specList);

        return enableHigherTlsOnPreLollipop(builder);
    }

    @NonNull
    public static OkHttpClient getCompatibleClient() {
        return getCompatibleClientBuilder().build();
    }

    /**
     * Add a custom User-Agent that contains Tusky & Android Version to all requests
     * Example:
     * User-Agent: Tusky/1.1.2 Android/5.0.2
     */
    @NonNull
    private static Interceptor getUserAgentInterceptor() {
        return new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                Request requestWithUserAgent = originalRequest.newBuilder()
                        .header("User-Agent", "Tusky/"+ BuildConfig.VERSION_NAME+" Android/"+Build.VERSION.RELEASE)
                        .build();
                return chain.proceed(requestWithUserAgent);
            }
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

    private static OkHttpClient.Builder enableHigherTlsOnPreLollipop(OkHttpClient.Builder builder) {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
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
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                builder.sslSocketFactory(new SSLSocketFactoryCompat(sslSocketFactory),
                        trustManager);
            } catch (NoSuchAlgorithmException|KeyStoreException|KeyManagementException e) {
                Log.e(TAG, "Failed enabling TLS 1.1 & 1.2. " + e.getMessage());
            }
        }

        return builder;
    }

    private static class SSLSocketFactoryCompat extends SSLSocketFactory {
        private static final String[] DESIRED_TLS_VERSIONS = { "TLSv1", "TLSv1.1", "TLSv1.2",
                "TLSv1.3" };

        final SSLSocketFactory delegate;

        SSLSocketFactoryCompat(SSLSocketFactory base) {
            this.delegate = base;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose)
                throws IOException {
            return patch(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return patch(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                                   int localPort) throws IOException {
            return patch(delegate.createSocket(address, port, localAddress, localPort));
        }

        @NonNull
        private static String[] getMatches(String[] wanted, String[] have) {
            List<String> a = new ArrayList<>(Arrays.asList(wanted));
            List<String> b = Arrays.asList(have);
            a.retainAll(b);
            return a.toArray(new String[0]);
        }

        private Socket patch(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                String[] protocols = getMatches(DESIRED_TLS_VERSIONS,
                        sslSocket.getSupportedProtocols());
                sslSocket.setEnabledProtocols(protocols);
            }
            return socket;
        }
    }
}
