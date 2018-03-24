package com.keylesspalace.tusky.di

import android.content.SharedPreferences
import android.text.Spanned
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.json.SpannedTypeAdapter
import com.keylesspalace.tusky.network.AuthInterceptor
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.OkHttpUtils
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Created by charlag on 3/24/18.
 */

@Module
class NetworkModule {
    @Provides
    @IntoMap()
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
    @IntoSet
    @Singleton
    fun providesAuthInterceptor(): Interceptor {
        // should accept AccountManager here probably but I don't want to break things yet
        return AuthInterceptor()
    }

    @Provides
    @IntoSet
    @Singleton
    fun providesLoggingInterceptor(): Interceptor {
        val level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        return HttpLoggingInterceptor().setLevel(level)
    }

    @Provides
    @Singleton
    fun providesHttpClient(interceptors: @JvmSuppressWildcards Set<Interceptor>,
                           preferences: SharedPreferences): OkHttpClient {
        return OkHttpUtils.getCompatibleClientBuilder(preferences)
                .apply {
                    interceptors.fold(this) { b, i ->
                        b.addInterceptor(i)
                    }
                }
                .build()
    }


    @Provides
    @Singleton
    fun providesRetrofit(httpClient: OkHttpClient,
                         converters: @JvmSuppressWildcards Set<Converter.Factory>): Retrofit {
        return Retrofit.Builder().baseUrl("https://dummy.placeholder/")
                .client(httpClient)
                .let { builder ->
                    // Doing it this way in case builder will be immutable so we return the final
                    // instance
                    converters.fold(builder) { b, c ->
                        b.addConverterFactory(c)
                    }
                }
                .build()

    }

    @Provides
    @Singleton
    fun providesApi(retrofit: Retrofit): MastodonApi = retrofit.create(MastodonApi::class.java)
}