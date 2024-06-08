package com.keylesspalace.tusky.appstore

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface Event

@Singleton
class EventHub @Inject constructor() {

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun dispatch(event: Event) {
        _events.emit(event)
    }
}
