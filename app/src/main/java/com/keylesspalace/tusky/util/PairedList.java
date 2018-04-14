package com.keylesspalace.tusky.util;

import android.arch.core.util.Function;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;


/**
 * This list implementation can help to keep two lists in sync - like real models and view models.
 * Every operation on the main list triggers update of the supplementary list (but not vice versa).
 * This makes sure that the main list is always the source of truth.
 * Main list is projected to the supplementary list by the passed mapper function.
 * Paired list is newer actually exposed and clients are provided with {@code getPairedCopy()},
 * {@code getPairedItem()} and {@code setPairedItem()}. This prevents modifications of the
 * supplementary list size so lists are always have the same length.
 * This implementation will not try to recover from exceptional cases so lists may be out of sync
 * after the exception.
 *
 * It is most useful with immutable data because we cannot track changes inside stored objects.
 * @param <T> type of elements in the main list
 * @param <V> type of elements in supplementary list
 */
public final class PairedList<T, V> extends AbstractList<T> {
    private final List<T> main = new ArrayList<>();
    private final List<V> synced = new ArrayList<>();
    private final Function<T, ? extends V> mapper;

    /**
     * Construct new paired list. Main and supplementary lists will be empty.
     * @param mapper Function, which will be used to translate items from the main list to the
     *               supplementary one.
     */
    public PairedList(Function<T, ? extends V> mapper) {
        this.mapper = mapper;
    }

    public List<V> getPairedCopy() {
        return new ArrayList<>(synced);
    }

    public V getPairedItem(int index) {
        return synced.get(index);
    }

    public void setPairedItem(int index, V element) {
        synced.set(index, element);
    }

    @Override
    public T get(int index) {
        return main.get(index);
    }

    @Override
    public T set(int index, T element) {
        synced.set(index, mapper.apply(element));
        return main.set(index, element);
    }

    @Override
    public boolean add(T t) {
        synced.add(mapper.apply(t));
        return main.add(t);
    }

    @Override
    public void add(int index, T element) {
        synced.add(index, mapper.apply(element));
        main.add(index, element);
    }

    @Override
    public T remove(int index) {
        synced.remove(index);
        return main.remove(index);
    }

    @Override
    public int size() {
        return main.size();
    }
}
