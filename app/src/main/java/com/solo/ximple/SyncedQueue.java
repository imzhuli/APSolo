package com.solo.ximple;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

public class SyncedQueue<T> {

    private AtomicReference<LinkedList<T>> listReference = new AtomicReference<>();

    public SyncedQueue() {
        listReference.set(new LinkedList<>());
    }

    public synchronized void put(T object) {
        LinkedList list = listReference.get();
        if (list == null) {
            list = new LinkedList<>();
        }
        list.add(object);
    }

    public synchronized T pop() {
        LinkedList list = listReference.get();
        if (list == null) {
            return null;
        }
        return (T)list.pop();
    }

    public synchronized LinkedList<T> grabQueue() {
        return listReference.getAndSet(new LinkedList<>());
    }

    public synchronized void clear() {
        LinkedList list = listReference.get();
        list.clear();
    }

}
