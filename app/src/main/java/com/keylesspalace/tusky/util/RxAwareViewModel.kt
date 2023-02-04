package com.keylesspalace.tusky.util

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable

open class RxAwareViewModel : ViewModel() {
    private val disposables = CompositeDisposable()

    fun Disposable.autoDispose() = disposables.add(this)

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}
