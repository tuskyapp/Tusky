package com.keylesspalace.tusky.util

import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

open class RxAwareViewModel : ViewModel() {
    val disposables = CompositeDisposable()

    fun Disposable.autoDispose() = disposables.add(this)

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}