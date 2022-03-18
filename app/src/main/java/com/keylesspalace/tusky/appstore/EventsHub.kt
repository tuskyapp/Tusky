package com.keylesspalace.tusky.appstore

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

interface Event
interface Dispatchable : Event

@Singleton
class EventHub @Inject constructor() {

    private val eventsSubject = PublishSubject.create<Event>()
    val events: Observable<Event> = eventsSubject

    fun dispatch(event: Dispatchable) {
        eventsSubject.onNext(event)
    }
}
