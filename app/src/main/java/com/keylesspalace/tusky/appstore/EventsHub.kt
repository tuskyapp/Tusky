package com.keylesspalace.tusky.appstore

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

interface Event

@Singleton
class EventHub @Inject constructor() {

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun dispatch(event: Event) {
        _events.emit(event)
    }

    //  TODO remove as soon as NotificationsFragment is Kotlin
    fun subscribe(lifecycleOwner: LifecycleOwner, consumer: Consumer<Event>) {
        lifecycleOwner.lifecycleScope.launch {
            events.collect { event ->
                consumer.accept(event)
            }
        }
    }
}
