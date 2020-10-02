/* Copyright 2018 Conny Duck
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
import com.keylesspalace.tusky.service.SendTootService
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.service.ServiceClientImpl
import com.keylesspalace.tusky.service.StreamingService
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector

@Module
abstract class ServicesModule {
    @ContributesAndroidInjector
    abstract fun contributesSendTootService(): SendTootService

    @ContributesAndroidInjector
    abstract fun contributesStreamingService(): StreamingService

    @Module
    companion object {
        @Provides
        @JvmStatic
        fun providesServiceClient(context: Context): ServiceClient {
            return ServiceClientImpl(context)
        }
    }
}