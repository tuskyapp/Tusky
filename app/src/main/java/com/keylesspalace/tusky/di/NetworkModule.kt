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
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.json.SpannedTypeAdapter
import com.keylesspalace.tusky.network.InstanceSwitchAuthInterceptor
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.OkHttpUtils
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
    @Singleton
    fun providesGson(): Gson {
        return GsonBuilder()
                .registerTypeAdapter(Spanned::class.java, SpannedTypeAdapter())
                .create()
    }

    @Provides
    @Singleton
    fun providesHttpClient(
            accountManager: AccountManager,
            context: Context
    ): OkHttpClient {
        return OkHttpUtils.getCompatibleClientBuilder(context)
                .apply {
                    addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
                    if (BuildConfig.DEBUG) {
                        addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
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
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .build()

    }

    @Provides
    @Singleton
    fun providesApi(retrofit: Retrofit): MastodonApi = retrofit.create(MastodonApi::class.java)
}