package com.keylesspalace.tusky.di

import android.content.Context
import com.keylesspalace.tusky.components.compose.MediaUploader
import com.keylesspalace.tusky.components.compose.MediaUploaderImpl
import com.keylesspalace.tusky.network.MastodonApi
import dagger.Module
import dagger.Provides

@Module
class MediaUploaderModule {
    @Provides
    fun providesMediaUploder(context: Context, mastodonApi: MastodonApi): MediaUploader =
            MediaUploaderImpl(context, mastodonApi)
}