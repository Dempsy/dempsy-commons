package net.dempsy.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class SpinLock {
    public static final int SPIN_TRIES = 100;
    private final AtomicInteger mine = new AtomicInteger(1);

    public class Guard implements AutoCloseable {
        @Override
        public void close() {
            release();
        }
    }

    public Guard guardedWait() {
        waitForLock();
        return new Guard();
    }

    public final void waitForLock() {
        int counter = SPIN_TRIES;
        do {
            if (mine.compareAndSet(1, 0))
                return;
            if (counter > 0)
                counter--;
            else LockSupport.parkNanos(1L);
        } while (true);
    }

    public final void release() {
        mine.lazySet(1);
    }

}
