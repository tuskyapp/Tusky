/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky.util

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object OkHttpUtils {
    fun getCompatibleClientBuilder(context: Context): OkHttpClient.Builder {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val httpProxyEnabled = preferences.getBoolean("httpProxyEnabled", false)
        val httpServer = preferences.getString("httpProxyServer", "")
        val httpPort = try {
            preferences.getString("httpProxyPort", "-1")!!.toInt()
        } catch (e: NumberFormatException) {
            // user has entered wrong port, fall back to no proxy
            -1
        }
        val cacheSize = 25 * 1024 * 1024 // 25 MiB
        val builder = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(context.cacheDir, cacheSize.toLong()))

        if (httpProxyEnabled && !httpServer!!.isEmpty() && httpPort > 0 && httpPort < 65535) {
            val address = InetSocketAddress.createUnresolved(httpServer, httpPort)
            builder.proxy(Proxy(Proxy.Type.HTTP, address))
        }
        return builder
    }

    /**
     * Add a custom User-Agent that contains Tusky & Android Version to all requests
     * Example:
     * User-Agent: Tusky/1.1.2 Android/5.0.2
     */
    private val userAgentInterceptor: Interceptor
        get() = Interceptor { chain: Interceptor.Chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header(
                    "User-Agent",
                    "Tusky/" + BuildConfig.VERSION_NAME + " Android/" + Build.VERSION.RELEASE
                )
                .build()
            chain.proceed(requestWithUserAgent)
        }
}
