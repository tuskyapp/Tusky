package com.keylesspalace.tusky.util

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

/**
 * Original code: https://medium.com/@Zhuinden/simple-one-liner-viewbinding-in-fragments-and-activities-with-kotlin-961430c6c07c
 * Refactor: https://bladecoder.medium.com/viewlifecyclelazy-and-other-ways-to-avoid-view-memory-leaks-in-android-fragments-4aa982e6e579
 */

inline fun <T : ViewBinding> AppCompatActivity.viewBinding(
    crossinline bindingInflater: (LayoutInflater) -> T
) = lazy(LazyThreadSafetyMode.NONE) {
    bindingInflater(layoutInflater)
}

private class ViewLifecycleLazy<T : Any>(
    private val fragment: Fragment,
    private val initializer: (View) -> T
) : Lazy<T>, LifecycleEventObserver {
    private var cached: T? = null

    override val value: T
        get() {
            return cached ?: run {
                val newValue = initializer(fragment.requireView())
                cached = newValue
                fragment.viewLifecycleOwner.lifecycle.addObserver(this)
                newValue
            }
        }

    override fun isInitialized() = cached != null

    override fun toString() = cached.toString()

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            cached = null
        }
    }
}

fun <T : ViewBinding> Fragment.viewBinding(viewBindingFactory: (View) -> T): Lazy<T> =
    ViewLifecycleLazy(this, viewBindingFactory)
