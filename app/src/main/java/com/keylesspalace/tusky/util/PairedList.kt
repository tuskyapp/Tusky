package com.keylesspalace.tusky.util

import androidx.arch.core.util.Function

/**
 * This list implementation can help to keep two lists in sync - like real models and view models.
 *
 * Every operation on the main list triggers update of the supplementary list (but not vice versa).
 *
 * This makes sure that the main list is always the source of truth.
 *
 * Main list is projected to the supplementary list by the passed mapper function.
 *
 * Paired list is newer actually exposed and clients are provided with `getPairedCopy()`,
 * `getPairedItem()` and `setPairedItem()`. This prevents modifications of the
 * supplementary list size so lists are always have the same length.
 *
 * This implementation will not try to recover from exceptional cases so lists may be out of sync
 * after the exception.
 *
 * It is most useful with immutable data because we cannot track changes inside stored objects.
 *
 * @param T type of elements in the main list
 * @param V type of elements in supplementary list
 * @param mapper Function, which will be used to translate items from the main list to the
 * supplementary one.
 * @constructor
 */
class PairedList<T, V> (private val mapper: Function<T, out V>) : AbstractMutableList<T>() {
    private val main: MutableList<T> = ArrayList()
    private val synced: MutableList<V> = ArrayList()

    val pairedCopy: List<V>
        get() = ArrayList(synced)

    fun getPairedItem(index: Int): V {
        return synced[index]
    }

    fun getPairedItemOrNull(index: Int): V? {
        return synced.getOrNull(index)
    }

    fun setPairedItem(index: Int, element: V) {
        synced[index] = element
    }

    override fun get(index: Int): T {
        return main[index]
    }

    override fun set(index: Int, element: T): T {
        synced[index] = mapper.apply(element)
        return main.set(index, element)
    }

    override fun add(element: T): Boolean {
        synced.add(mapper.apply(element))
        return main.add(element)
    }

    override fun add(index: Int, element: T) {
        synced.add(index, mapper.apply(element))
        main.add(index, element)
    }

    override fun removeAt(index: Int): T {
        synced.removeAt(index)
        return main.removeAt(index)
    }

    override val size: Int
        get() = main.size
}
