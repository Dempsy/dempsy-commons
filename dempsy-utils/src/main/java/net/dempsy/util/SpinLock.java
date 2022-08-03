/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            if(mine.compareAndSet(1, 0))
                return;
            if(counter > 0)
                counter--;
            else
                LockSupport.parkNanos(1L);
        } while(true);
    }

    public final void release() {
        mine.lazySet(1);
    }

}
