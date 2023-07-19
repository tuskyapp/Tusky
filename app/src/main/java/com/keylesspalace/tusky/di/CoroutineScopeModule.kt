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

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier

/**
 * Scope for potentially long-running tasks that should outlive the viewmodel that
 * started them. For example, if the API call to bookmark a status is taking a long
 * time, that call should not be cancelled because the user has navigated away from
 * the viewmodel that made the call.
 *
 * @see https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

@Module
class CoroutineScopeModule {
    @ApplicationScope
    @Provides
    fun providesApplicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
