package com.keylesspalace.tusky.appstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

interface Event

@Singleton
class EventHub @Inject constructor() {

    private val sharedEventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val events: Flow<Event> = sharedEventFlow

    suspend fun dispatch(event: Event) {
        sharedEventFlow.emit(event)
    }
}
