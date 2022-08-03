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

package net.dempsy.util.executor;

import static net.dempsy.util.Functional.ignore;

import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.dempsy.util.QuietCloseable;

/**
 * This class is primarily meant to be used with routines that execute blocking IO.
 * If an MP in Dempsy ends up doing IO that takes too long, then all of the worker
 * threads could end up in those IO routines starving the system of processing for
 * other MPs. This can be used to manage the IO routines and allow them to be
 * interrupted if they take too long.
 */
public class AsyncTaskExecutorWithMonitor implements QuietCloseable {

    private Thread monitorThread;

    private final ThreadPoolExecutor workers;
    private final String poolBaseName;
    private final long timeoutMillis;
    private final AtomicBoolean stop = new AtomicBoolean(false);

    private static int theadCount = 0;

    public AsyncTaskExecutorWithMonitor(final int numThreads, final String poolBaseName, final long timeoutMillis) {

        this.poolBaseName = poolBaseName;
        workers = (ThreadPoolExecutor)Executors.newFixedThreadPool(numThreads, r -> new Thread(r, poolBaseName + "-" + (theadCount++)));
        monitorThread = null;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public synchronized void close() {
        stop.set(true);
        workers.shutdownNow();
    }

    public ThreadPoolExecutor getExecutor() {
        return workers;
    }

    private class ProxyRunnable implements Runnable {

        final Runnable runnable;
        final Consumer<Thread> action;
        Thread currentThread = null;
        long runTime = -1;

        ProxyRunnable(final Runnable r, final Consumer<Thread> action) {
            this.runnable = r;
            this.action = action == null ? t -> Optional.ofNullable(t).ifPresent(t1 -> t1.interrupt()) : action;
        }

        public void interrupt() {
            synchronized(this) {
                action.accept(currentThread);
            }
        }

        @Override
        public void run() {
            synchronized(this) {
                runTime = System.currentTimeMillis();
                currentThread = Thread.currentThread();
            }

            monitor(this);

            try {
                runnable.run();
            } finally {
                synchronized(this) {
                    currentThread = null;
                }
            }
        }

    }

    public void submit(final Runnable r) {
        submit(r, null);
    }

    public synchronized void submit(final Runnable r, final Consumer<Thread> action) {
        if(stop.get())
            throw new IllegalStateException("submit called on a closed " + AsyncTaskExecutorWithMonitor.class.getSimpleName());
        workers.submit(new ProxyRunnable(r, action));
    }

    private final LinkedList<ProxyRunnable> jobs = new LinkedList<>();

    private void monitor(final ProxyRunnable r) {

        synchronized(this) {
            jobs.add(r);

            if(monitorThread == null) {
                monitorThread = new Thread(() -> {

                    try {
                        boolean done = false;
                        while(!done) {
                            if(stop.get())
                                break;
                            final ProxyRunnable job;

                            // get the oldest job or if there is none, go to sleep for a while
                            // then try again (continue).
                            synchronized(this) {
                                job = jobs.size() > 0 ? jobs.removeLast() : null;
                            }

                            if(job == null) {
                                ignore(() -> Thread.sleep(timeoutMillis)); // there wont be anything to do for at least this long
                                continue;
                            }

                            synchronized(job) {
                                // There was a job on the list but it's no longer running
                                if(job.currentThread == null)
                                    continue;
                            }

                            final long waitTime;
                            synchronized(job) {
                                // if not expired, wait until it is.
                                waitTime = (job.runTime + timeoutMillis) - System.currentTimeMillis();
                            }

                            if(waitTime > 0) {
                                try {
                                    Thread.sleep(waitTime);
                                } catch(final InterruptedException ie) {
                                    // we'll stick it back on the list as "next" so we can just go try again
                                    synchronized(this) {
                                        jobs.addLast(job);
                                    }
                                    continue; // well, just go back and try another
                                }
                            }

                            // it's expired.
                            synchronized(job) {
                                // if the current job is still active
                                if(job.currentThread != null)
                                    job.interrupt();
                            }

                            synchronized(this) {
                                done = jobs.size() == 0;
                                if(done)
                                    monitorThread = null;
                            }
                        }
                    } finally {
                        synchronized(this) {
                            if(monitorThread != null)
                                monitorThread = null;
                        }

                    }

                }, poolBaseName + "-monitor");

                monitorThread.setDaemon(true);
                monitorThread.start();
            }
        }
    }
}
