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
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.json.Guarded
import com.keylesspalace.tusky.json.GuardedAdapter
import com.keylesspalace.tusky.json.NotificationTypeAdapter
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.MediaUploadApi
import com.keylesspalace.tusky.network.apiForAccount
import com.keylesspalace.tusky.settings.PrefKeys.HTTP_PROXY_ENABLED
import com.keylesspalace.tusky.settings.PrefKeys.HTTP_PROXY_PORT
import com.keylesspalace.tusky.settings.PrefKeys.HTTP_PROXY_SERVER
import com.keylesspalace.tusky.settings.ProxyConfiguration
import com.keylesspalace.tusky.util.getNonNullString
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Created by charlag on 3/24/18.
 */

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "NetworkModule"

    @Provides
    @Named("defaultPort")
    fun providesDefaultPort(): Int {
        return 443
    }

    @Provides
    @Named("defaultScheme")
    fun providesDefaultScheme(): String {
        return "https://"
    }

    @Provides
    @Singleton
    fun providesMoshi(): Moshi = Moshi.Builder()
        .add(GuardedAdapter.ANNOTATION_FACTORY)
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        // Enum types with fallback value
        .add(
            Attachment.Type::class.java,
            EnumJsonAdapter.create(Attachment.Type::class.java)
                .withUnknownFallback(Attachment.Type.UNKNOWN)
        )
        .add(
            Notification.Type::class.java,
            NotificationTypeAdapter()
        )
        .add(
            Status.Visibility::class.java,
            EnumJsonAdapter.create(Status.Visibility::class.java)
                .withUnknownFallback(Status.Visibility.UNKNOWN)
        )
        .build()

    @Provides
    @Singleton
    fun providesHttpClient(
        @ApplicationContext context: Context,
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
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(
        httpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://${MastodonApi.PLACEHOLDER_DOMAIN}")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(NetworkResultCallAdapterFactory.create())
            .build()
    }

    @Provides
    fun providesMastodonApi(
        httpClient: OkHttpClient,
        retrofit: Retrofit,
        accountManager: AccountManager
    ): MastodonApi {
        return apiForAccount(accountManager.activeAccount, httpClient, retrofit)
    }

    @Provides
    fun providesMediaUploadApi(
        retrofit: Retrofit,
        okHttpClient: OkHttpClient,
        accountManager: AccountManager
    ): MediaUploadApi {
        val longTimeOutOkHttpClient = okHttpClient.newBuilder()
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .build()

        return apiForAccount(accountManager.activeAccount, longTimeOutOkHttpClient, retrofit)
    }
}
