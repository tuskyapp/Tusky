/* Copyright 2018 charlag
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

package com.keylesspalace.tusky.di

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import at.connyduck.calladapter.networkresult.NetworkResultCallAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.json.Rfc3339DateJsonAdapter
import com.keylesspalace.tusky.network.InstanceSwitchAuthInterceptor
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.MediaUploadApi
import com.keylesspalace.tusky.settings.PrefKeys.HTTP_PROXY_ENABLED
import com.keylesspalace.tusky.settings.PrefKeys.HTTP_PROXY_PORT
import com.keylesspalace.tusky.settings.PrefKeys.HTTP_PROXY_SERVER
import com.keylesspalace.tusky.settings.ProxyConfiguration
import com.keylesspalace.tusky.util.getNonNullString
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Created by charlag on 3/24/18.
 */

@Module
class NetworkModule {

    @Provides
    @Singleton
    fun providesGson(): Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, Rfc3339DateJsonAdapter())
        .create()

    @Provides
    @Singleton
    fun providesHttpClient(
        accountManager: AccountManager,
        context: Context,
        preferences: SharedPreferences
    ): OkHttpClient {
        val httpProxyEnabled = preferences.getBoolean(HTTP_PROXY_ENABLED, false)
        val httpServer = preferences.getNonNullString(HTTP_PROXY_SERVER, "")
        val httpPort = preferences.getNonNullString(HTTP_PROXY_PORT, "-1").toIntOrNull() ?: -1
        val cacheSize = 25 * 1024 * 1024L // 25 MiB
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                /**
                 * Add a custom User-Agent that contains Tusky, Android and OkHttp Version to all requests
                 * Example:
                 * User-Agent: Tusky/1.1.2 Android/5.0.2 OkHttp/4.9.0
                 * */
                val requestWithUserAgent = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Tusky/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.RELEASE} OkHttp/${OkHttp.VERSION}"
                    )
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(context.cacheDir, cacheSize))

        if (httpProxyEnabled) {
            ProxyConfiguration.create(httpServer, httpPort)?.also { conf ->
                val address = InetSocketAddress.createUnresolved(IDN.toASCII(conf.hostname), conf.port)
                builder.proxy(Proxy(Proxy.Type.HTTP, address))
            } ?: Log.w(TAG, "Invalid proxy configuration: ($httpServer, $httpPort)")
        }

        return builder
            .apply {
                addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(
        httpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder().baseUrl("https://" + MastodonApi.PLACEHOLDER_DOMAIN)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addCallAdapterFactory(NetworkResultCallAdapterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providesApi(retrofit: Retrofit): MastodonApi = retrofit.create()

    @Provides
    @Singleton
    fun providesMediaUploadApi(retrofit: Retrofit, okHttpClient: OkHttpClient): MediaUploadApi {
        val longTimeOutOkHttpClient = okHttpClient.newBuilder()
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .build()

        return retrofit.newBuilder()
            .client(longTimeOutOkHttpClient)
            .build()
            .create()
    }

    companion object {
        private const val TAG = "NetworkModule"
    }
}
