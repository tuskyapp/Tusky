package com.keylesspalace.tusky.util

import androidx.lifecycle.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.Single

inline fun <X, Y> LiveData<X>.map(crossinline mapFunction: (X) -> Y): LiveData<Y> =
        Transformations.map(this) { input -> mapFunction(input) }

inline fun <X, Y> LiveData<X>.switchMap(
        crossinline switchMapFunction: (X) -> LiveData<Y>
): LiveData<Y> = Transformations.switchMap(this) { input -> switchMapFunction(input) }

inline fun <X> LiveData<X>.filter(crossinline predicate: (X) -> Boolean): LiveData<X> {
    val liveData = MediatorLiveData<X>()
    liveData.addSource(this) { value ->
        if (predicate(value)) {
            liveData.value = value
        }
    }
    return liveData
}

fun LifecycleOwner.withLifecycleContext(body: LifecycleContext.() -> Unit) =
        LifecycleContext(this).apply(body)

class LifecycleContext(val lifecycleOwner: LifecycleOwner) {
    inline fun <T> LiveData<T>.observe(crossinline observer: (T) -> Unit) =
            this.observe(lifecycleOwner, Observer { observer(it) })

    /**
     * Just hold a subscription,
     */
    fun <T> LiveData<T>.subscribe() =
            this.observe(lifecycleOwner, Observer { })
}

fun <A, B, R> combineLiveData(a: LiveData<A>, b: LiveData<B>, combiner: (A, B) -> R): LiveData<R> {
    val liveData = MediatorLiveData<R>()
    liveData.addSource(a) {
        if (a.value != null && b.value != null) {
            liveData.value = combiner(a.value!!, b.value!!)
        }
    }
    liveData.addSource(b) {
        if (a.value != null && b.value != null) {
            liveData.value = combiner(a.value!!, b.value!!)
        }
    }
    return liveData
}

fun <T> Single<T>.toLiveData() = LiveDataReactiveStreams.fromPublisher(this.toFlowable())
fun <T> Observable<T>.toLiveData(
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.LATEST
) = LiveDataReactiveStreams.fromPublisher(this.toFlowable(BackpressureStrategy.LATEST))