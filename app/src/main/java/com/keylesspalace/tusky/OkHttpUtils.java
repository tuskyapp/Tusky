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

package com.keylesspalace.tusky;

import android.os.Build;
import android.support.annotation.NonNull;

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

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

class OkHttpUtils {
    static final String TAG = "OkHttpUtils"; // logging tag

    /**
     * Makes a Builder with the maximum range of TLS versions and cipher suites enabled.
     *
     * It first tries the "approved" list of cipher suites given in OkHttp (the default in
     * ConnectionSpec.MODERN_TLS) and if that doesn't work falls back to the set of ALL enabled,
     * then falls back to plain http.
     *
     * TLS 1.1 and 1.2 have to be manually enabled on API levels 16-20.
     */
    @NonNull
    static OkHttpClient.Builder getCompatibleClientBuilder() {
        ConnectionSpec fallback = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .allEnabledCipherSuites()
                .supportsTlsExtensions(true)
                .build();

        List<ConnectionSpec> specList = new ArrayList<>();
        specList.add(ConnectionSpec.MODERN_TLS);
        specList.add(fallback);
        specList.add(ConnectionSpec.CLEARTEXT);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectionSpecs(specList);

        return enableHigherTlsOnPreLollipop(builder);
    }

    @NonNull
    static OkHttpClient getCompatibleClient() {
        return getCompatibleClientBuilder().build();
    }

    private static OkHttpClient.Builder enableHigherTlsOnPreLollipop(OkHttpClient.Builder builder) {
        // if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
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
        // }

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

        @NonNull
        private static List<String> getDifferences(String[] wanted, String[] have) {
            List<String> a = new ArrayList<>(Arrays.asList(wanted));
            List<String> b = Arrays.asList(have);
            a.removeAll(b);
            return a;
        }

        private Socket patch(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                String[] protocols = getMatches(DESIRED_TLS_VERSIONS,
                        sslSocket.getSupportedProtocols());
                sslSocket.setEnabledProtocols(protocols);

                // Add a debug listener.
                String[] enabledProtocols = sslSocket.getEnabledProtocols();
                List<String> disabledProtocols = getDifferences(sslSocket.getSupportedProtocols(),
                        enabledProtocols);
                String[] enabledSuites = sslSocket.getEnabledCipherSuites();
                List<String> disabledSuites = getDifferences(sslSocket.getSupportedCipherSuites(),
                        enabledSuites);
                Log.i(TAG, "Socket Created-----");
                Log.i(TAG, "enabled protocols: " + Arrays.toString(enabledProtocols));
                Log.i(TAG, "disabled protocols: " + disabledProtocols.toString());
                Log.i(TAG, "enabled cipher suites: " + Arrays.toString(enabledSuites));
                Log.i(TAG, "disabled cipher suites: " + disabledSuites.toString());

                sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
                    @Override
                    public void handshakeCompleted(HandshakeCompletedEvent event) {
                        String host = event.getSession().getPeerHost();
                        String protocol = event.getSession().getProtocol();
                        String cipherSuite = event.getCipherSuite();
                        Log.i(TAG, String.format("Handshake: %s %s %s", host, protocol,
                                cipherSuite));
                    }
                });
            }
            return socket;
        }
    }
}
