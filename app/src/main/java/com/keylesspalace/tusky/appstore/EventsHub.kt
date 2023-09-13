package com.keylesspalace.tusky.appstore

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

interface Event

@Singleton
class EventHub @Inject constructor() {

    private val sharedEventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val events: Flow<Event> = sharedEventFlow

    //  TODO remove this old stuff as soon as NotificationsFragment is Kotlin
    private val eventsSubject = PublishSubject.create<Event>()
    val eventsObservable: Observable<Event> = eventsSubject

    suspend fun dispatch(event: Event) {
        sharedEventFlow.emit(event)
        eventsSubject.onNext(event)
    }

    fun dispatchOld(event: Event) {
        eventsSubject.onNext(event)
    }
}
