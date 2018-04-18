package com.keylesspalace.tusky.di

import com.keylesspalace.tusky.receiver.SendStatusBroadcastReceiver
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class BroadcastReceiverModule {
    @ContributesAndroidInjector
    abstract fun contributeSendStatusBroadcastReceiver() : SendStatusBroadcastReceiver
}