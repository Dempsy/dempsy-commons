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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an executor with a priority queue allowing priority jobs to be
 * inserted in the front of the queue. It also allows access to the queue
 * itself, usually hidden in most other implementations. it can optionally
 * have a limited capacity.
 */
public class SimpleExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleExecutor.class);

    private static final int NUM_POLL_TRIES_ON_SHUTDOWN = 100;

    private final ThreadFactory threadFactory;
    private final LinkedBlockingDeque<Runnable> jobQueue;
    private final int numThreads;
    private final Thread[] threads;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicBoolean gracefullShutdown = new AtomicBoolean(false);

    public SimpleExecutor(final int numThreads, final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.numThreads = numThreads;
        this.threads = new Thread[numThreads];
        jobQueue = new LinkedBlockingDeque<>();
        start();
    }

    public SimpleExecutor(final int numThreads, final ThreadFactory threadFactory, final int queueLimit) {
        this.threadFactory = threadFactory;
        this.numThreads = numThreads;
        this.threads = new Thread[numThreads];
        jobQueue = new LinkedBlockingDeque<>(queueLimit);
        start();
    }

    public boolean submit(final Runnable job) {
        if(stop.get())
            return false;
        return jobQueue.offer(job);
    }

    public boolean submitFirst(final Runnable job) {
        if(stop.get())
            return false;
        return jobQueue.offerFirst(job);
    }

    public LinkedBlockingDeque<Runnable> getQueue() {
        return jobQueue;
    }

    public void shutdown() {
        gracefullShutdown.set(true);
        synchronized(this) {
            stop.set(true);
        } // full memory barrier
    }

    public List<Runnable> shutdownNow() {
        gracefullShutdown.set(false);
        synchronized(this) {
            stop.set(true);
        } // full memory barrier

        do {
            Arrays.stream(threads).filter(t -> t.isAlive()).forEach(t -> t.interrupt());
        } while(Arrays.stream(threads).filter(t -> t.isAlive()).findAny().isPresent());

        final ArrayList<Runnable> ret = new ArrayList<>();
        jobQueue.drainTo(ret);
        return ret;
    }

    private void start() {
        stop.set(false);
        for(int i = 0; i < numThreads; i++) {
            threads[i] = threadFactory.newThread(new Worker(jobQueue, stop, gracefullShutdown));
            threads[i].setDaemon(true); // just in case.
            threads[i].start();
        }
    }

    private static class Worker implements Runnable {
        final LinkedBlockingDeque<Runnable> jobQueue;
        final AtomicBoolean stop;
        final AtomicBoolean gracefullShutdown;

        Worker(final LinkedBlockingDeque<Runnable> queue, final AtomicBoolean stopped, final AtomicBoolean gracefullShutdown) {
            this.jobQueue = queue;
            this.stop = stopped;
            this.gracefullShutdown = gracefullShutdown;
        }

        @Override
        public void run() {
            try {
                while(!stop.get()) {
                    try {
                        final Runnable job = jobQueue.take();
                        if(job != null) {
                            job.run();
                        } else {
                            LOGGER.warn("Retrieved a null job from the queue.");
                        }
                    } catch(final InterruptedException ie) {
                        if(!stop.get())
                            LOGGER.warn("Worker interrupted but we're not stopping.");
                    } catch(final RuntimeException rte) {
                        LOGGER.info("Job threw exception", rte);
                    }
                }

                if(gracefullShutdown.get()) {
                    // let any remaining jobs execute
                    int pollTries = 0;
                    for(boolean done = false; !done;) {
                        try {
                            final Runnable job = jobQueue.poll();
                            done = job == null && pollTries > NUM_POLL_TRIES_ON_SHUTDOWN;
                            if(job != null) {
                                pollTries = 0;
                                job.run();
                            } else
                                pollTries++;
                        } catch(final RuntimeException rte) {
                            LOGGER.info("Job threw exception", rte);
                        }
                    }
                }
            } catch(final RuntimeException rte) {
                LOGGER.error("Catastrohpic exception in runner. Thread died", rte);
                throw rte;
            } catch(final Throwable th) {
                LOGGER.error("Catastrohpic exception in runner. Thread died", th);
                throw new RuntimeException(th);
            } finally {
                LOGGER.info("Shutting down worker thread from pool");
            }
        }
    }
}
