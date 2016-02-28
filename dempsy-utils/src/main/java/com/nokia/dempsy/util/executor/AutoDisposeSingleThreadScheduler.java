/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nokia.dempsy.util.executor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is a scheduler that will schedule a task to run in the future (or now) and when that task completes it shuts itself down. It can be thought of almost as a daemon task scheduler.
 */
public final class AutoDisposeSingleThreadScheduler {
    private final String baseThreadName;
    private final AtomicLong pendingCalls = new AtomicLong(0);

    private final AtomicLong sequence = new AtomicLong(0);
    private final Runnable nameSetter = new Runnable() {
        @Override
        public void run() {
            Thread.currentThread().setName(baseThreadName + "-" + sequence.getAndIncrement());
        }
    };

    public AutoDisposeSingleThreadScheduler(final String baseThreadName) {
        this.baseThreadName = baseThreadName;
    }

    /**
     * This object is returned from {@see AutoDisposeSingleThreadScheduler#schedule(Runnable, long, TimeUnit)} anc can be used to cancel the task - best effort.
     */
    public class Cancelable {
        private final ScheduledFuture<?> future;
        private final RunnableProxy runnable;

        private Cancelable(final RunnableProxy runnable, final ScheduledFuture<?> future) {
            this.runnable = runnable;
            this.future = future;
        }

        /**
         * Attempt to cancel the Runnable that was submitted to {@see AutoDisposeSingleThreadScheduler#schedule(Runnable, long, TimeUnit)}
         */
        public void cancel() {
            future.cancel(false);
            if (runnable.decrement() == 0)
                disposeOfScheduler();
        }

        /**
         * Is the Runnable that was submitted to {@see AutoDisposeSingleThreadScheduler#schedule(Runnable, long, TimeUnit)} comleted?
         */
        public boolean isDone() {
            return future.isDone();
        }
    }

    /**
     * Schedule the given Runnable to run at the given time period from now. The scheduler resources will be cleaned up once the task runs.
     */
    public synchronized Cancelable schedule(final Runnable runnable, final long timeout, final TimeUnit units) {
        pendingCalls.incrementAndGet();
        final RunnableProxy proxy = new RunnableProxy(runnable);
        return new Cancelable(proxy, getScheduledExecutor().schedule(proxy, timeout, units));
    }

    private class RunnableProxy implements Runnable {
        final Runnable proxied;
        final AtomicBoolean decremented = new AtomicBoolean(false);

        private RunnableProxy(final Runnable proxied) {
            this.proxied = proxied;
        }

        @Override
        public void run() {
            // running the proxied can resubmit the task ... so we dispose afterward
            try {
                proxied.run();
            } finally {
                if (decrement() == 0)
                    disposeOfScheduler();
            }
        }

        private long decrement() {
            return decremented.getAndSet(true) ? Long.MAX_VALUE : pendingCalls.decrementAndGet();
        }
    }

    private ScheduledExecutorService scheduler = null;

    private synchronized final ScheduledExecutorService getScheduledExecutor() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1);
            if (baseThreadName != null)
                scheduler.execute(nameSetter);
        }
        return scheduler;
    }

    private synchronized final void disposeOfScheduler() {
        if (scheduler != null)
            scheduler.shutdown();
        scheduler = null;
    }
}
