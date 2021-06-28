package com.keylesspalace.tusky.appstore

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject

interface Event
interface Dispatchable : Event

interface EventHub {
    val events: Observable<Event>
    fun dispatch(event: Dispatchable)
}

object EventHubImpl : EventHub {

    private val eventsSubject = PublishSubject.create<Event>()
    override val events: Observable<Event> = eventsSubject

    override fun dispatch(event: Dispatchable) {
        eventsSubject.onNext(event)
    }
}
