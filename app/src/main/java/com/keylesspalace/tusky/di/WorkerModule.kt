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

import androidx.work.ListenableWorker
import com.keylesspalace.tusky.worker.ChildWorkerFactory
import com.keylesspalace.tusky.worker.NotificationWorker
import com.keylesspalace.tusky.worker.PruneCacheWorker
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

@Module
abstract class WorkerModule {
    @Binds
    @IntoMap
    @WorkerKey(NotificationWorker::class)
    internal abstract fun bindNotificationWorkerFactory(worker: NotificationWorker.Factory): ChildWorkerFactory

    @Binds
    @IntoMap
    @WorkerKey(PruneCacheWorker::class)
    internal abstract fun bindPruneCacheWorkerFactory(worker: PruneCacheWorker.Factory): ChildWorkerFactory
}
