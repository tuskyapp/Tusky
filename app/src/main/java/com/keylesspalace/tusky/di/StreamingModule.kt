package com.keylesspalace.tusky.di

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.keylesspalace.tusky.ProfileStreamListener
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.network.MastodonApi
import dagger.Module
import dagger.Provides

// Interface is for Java
interface ProfileStreamingListenerProvider {
    fun get(lifecycleOwner: LifecycleOwner): ProfileStreamListener
}

@Module
class StreamingModule {
    @Provides
    fun providesSProfileStreamingListener(
            api: MastodonApi,
            gson: Gson,
            context: Context,
            accountManager: AccountManager,
            eventHub: EventHub
    ): ProfileStreamingListenerProvider = object : ProfileStreamingListenerProvider {
        override fun get(lifecycleOwner: LifecycleOwner) = ProfileStreamListener(api, gson, context, accountManager, eventHub, lifecycleOwner)
    }
}