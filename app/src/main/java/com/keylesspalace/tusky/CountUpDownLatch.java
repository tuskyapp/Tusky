package com.keylesspalace.tusky;

public class CountUpDownLatch {
    private int count;

    public CountUpDownLatch() {
        this.count = 0;
    }

    public synchronized void countDown() {
        count--;
        notifyAll();
    }

    public synchronized void countUp() {
        count++;
        notifyAll();
    }

    public synchronized void await() throws InterruptedException {
        while (count != 0) {
            wait();
        }
    }
}
