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
import android.text.Spanned
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.json.SpannedTypeAdapter
import com.keylesspalace.tusky.network.InstanceSwitchAuthInterceptor
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.OkHttpUtils
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Created by charlag on 3/24/18.
 */

@Module
class NetworkModule {
    @Provides
    @IntoMap
    @ClassKey(Spanned::class)
    fun providesSpannedTypeAdapter(): JsonDeserializer<*> = SpannedTypeAdapter()

    @Provides
    @Singleton
    fun providesGson(adapters: @JvmSuppressWildcards Map<Class<*>, JsonDeserializer<*>>): Gson {
        return GsonBuilder()
                .apply {
                    for ((k, v) in adapters) {
                        registerTypeAdapter(k, v)
                    }
                }
                .create()
    }

    @Provides
    @IntoSet
    @Singleton
    fun providesConverterFactory(gson: Gson): Converter.Factory = GsonConverterFactory.create(gson)

    @Provides
    @Singleton
    fun providesHttpClient(accountManager: AccountManager,
                           context: Context): OkHttpClient {
        return OkHttpUtils.getCompatibleClientBuilder(context)
                .apply {
                    addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
                    if (BuildConfig.DEBUG) {
                        addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                    }
                }
                .build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(httpClient: OkHttpClient,
                         converters: @JvmSuppressWildcards Set<Converter.Factory>): Retrofit {
        return Retrofit.Builder().baseUrl("https://" + MastodonApi.PLACEHOLDER_DOMAIN)
                .client(httpClient)
                .let { builder ->
                    // Doing it this way in case builder will be immutable so we return the final
                    // instance
                    converters.fold(builder) { b, c ->
                        b.addConverterFactory(c)
                    }
                    builder.addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                }
                .build()

    }

    @Provides
    @Singleton
    fun providesApi(retrofit: Retrofit): MastodonApi = retrofit.create(MastodonApi::class.java)
}