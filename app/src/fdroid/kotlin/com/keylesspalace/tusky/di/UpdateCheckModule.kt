/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.di

import at.connyduck.calladapter.networkresult.NetworkResultCallAdapterFactory
import com.google.gson.Gson
import com.keylesspalace.tusky.updatecheck.FdroidService
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import javax.inject.Singleton

@Module
class UpdateCheckModule {
    @Provides
    @Singleton
    fun providesFdroidService(
        httpClient: OkHttpClient,
        gson: Gson
    ): FdroidService = Retrofit.Builder()
        .baseUrl("https://f-droid.org")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(NetworkResultCallAdapterFactory.create())
        .build()
        .create()
}
