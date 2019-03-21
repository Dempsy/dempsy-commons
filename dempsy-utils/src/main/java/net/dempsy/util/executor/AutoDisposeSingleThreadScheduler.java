/*
 * Copyright 2012 the original author or authors.
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

package net.dempsy.util.executor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is a scheduler that will schedule a task to run in the future (or now) and when that task completes it shuts itself down. It can be thought of almost as
 * a daemon task scheduler.
 */
public final class AutoDisposeSingleThreadScheduler {
    private final String baseThreadName;
    private long pendingCalls = 0L;

    private final AtomicLong sequence = new AtomicLong(0);
    private ScheduledExecutorService scheduler = null;

    public AutoDisposeSingleThreadScheduler(final String baseThreadName) {
        this.baseThreadName = baseThreadName;
    }

    /**
     * This object is returned from {@link AutoDisposeSingleThreadScheduler#schedule(Runnable, long, TimeUnit)} and can be used to cancel the task - best
     * effort.
     */
    public class Cancelable implements Runnable {
        private boolean cancelled = false;

        private final ScheduledFuture<?> future;
        private final Runnable proxied;

        // called only with a lock on the outer instance.
        private Cancelable(final Runnable runnable, final long timeout, final TimeUnit units) {
            this.proxied = runnable;
            this.future = getScheduledExecutor().schedule(this, timeout, units);
            pendingCalls++;
        }

        /**
         * Attempt to cancel the Runnable that was submitted to {@link AutoDisposeSingleThreadScheduler#schedule(Runnable, long, TimeUnit)}
         */
        public void cancel() {
            synchronized(AutoDisposeSingleThreadScheduler.this) {
                future.cancel(false);
                cancelled = true;
                decrement();
            }
        }

        @Override
        public void run() {
            // running the proxied can resubmit the task ... so we dispose afterward
            synchronized(AutoDisposeSingleThreadScheduler.this) {
                if(cancelled)
                    return;
            }

            try {
                proxied.run();
            } finally {
                synchronized(AutoDisposeSingleThreadScheduler.this) {
                    if(!cancelled) // if it was cancelled then it was already decremented
                        decrement();
                }
            }
        }

        /**
         * Is the Runnable that was submitted to {@link AutoDisposeSingleThreadScheduler#schedule(Runnable, long, TimeUnit)} comleted?
         */
        public boolean isDone() {
            return future.isDone();
        }

        private void decrement() {
            synchronized(AutoDisposeSingleThreadScheduler.this) {
                pendingCalls--;
                if(pendingCalls <= 0)
                    disposeOfScheduler();
            }
        }
    }

    /**
     * Schedule the given Runnable to run at the given time period from now. The scheduler resources will be cleaned up once the task runs.
     */
    public synchronized Cancelable schedule(final Runnable runnable, final long timeout, final TimeUnit units) {
        return new Cancelable(runnable, timeout, units);
    }

    private synchronized final ScheduledExecutorService getScheduledExecutor() {
        if(scheduler == null) {
            if(baseThreadName != null)
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, baseThreadName + "-" + sequence.getAndIncrement()));
            else
                scheduler = Executors.newSingleThreadScheduledExecutor();

        }
        return scheduler;
    }

    private synchronized final void disposeOfScheduler() {
        if(scheduler != null)
            scheduler.shutdown();
        scheduler = null;
    }
}
