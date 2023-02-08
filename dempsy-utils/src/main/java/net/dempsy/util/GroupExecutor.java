package net.dempsy.util;

import static net.dempsy.util.Functional.uncheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an executor that groups together several queues and applies a single thread
 * pool to the set of queues. Each queue is guaranteed to have only 1 thread at a time
 * working off the
 */
public class GroupExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupExecutor.class);

    private static final int NUM_POLL_TRIES_ON_SHUTDOWN = 100;

    private final LinkedBlockingDeque<LinkedBlockingDeque<Runnable>> jobQueues = new LinkedBlockingDeque<>();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final ThreadFactory threadFactory;
    private final int numThreads;
    private final Thread[] threads;
    private final AtomicBoolean gracefullShutdown = new AtomicBoolean(false);

    final CountDownLatch startingLatch;

    public GroupExecutor(final int numThreads, final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.numThreads = numThreads;
        this.threads = new Thread[numThreads];
        startingLatch = new CountDownLatch(numThreads);
        start();
        uncheck(() -> startingLatch.await());
    }

    public final class Queue {
        private final LinkedBlockingDeque<Runnable> jobQueue;

        private Queue(final int queueLimit) {
            jobQueue = new LinkedBlockingDeque<>(queueLimit);
        }

        private Queue() {
            jobQueue = new LinkedBlockingDeque<>();
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

        public int size() {
            return jobQueue.size();
        }

        public LinkedBlockingDeque<Runnable> getQueue() {
            return jobQueue;
        }
    }

    /**
     * Create a new executor with an unbounded queue
     */
    public Queue newExecutor() {
        final var ret = new Queue();
        jobQueues.add(ret.getQueue());
        return ret;
    }

    /**
     * Create a new executor with the given limit
     */
    public Queue newExecutor(final int queueLimit) {
        final var ret = new Queue(queueLimit);
        jobQueues.add(ret.getQueue());
        return ret;
    }

    public void shutdown() {
        gracefullShutdown.set(true);
        synchronized(this) {
            stop.set(true);
        } // full memory barrier
          // kick out all of the threads that might be waiting.

        // the -1 is to avoid interrupting the primary thread which will do the cleanup.
        for(int i = 0; i < (threads.length - 1); i++) {
            threads[i].interrupt();
        }
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
        jobQueues.forEach(q -> q.drainTo(ret));

        // the -1 is to avoid interrupting the primary
        // thread which will do the cleanup.
        for(int i = 0; i < (threads.length - 1); i++)
            threads[i].interrupt();

        return ret;
    }

    private final AtomicLong runningNonPrimaryThreads = new AtomicLong(0);

    private void start() {
        stop.set(false);
        GroupWorker prev = null;
        for(int i = 0; i < numThreads; i++) {
            final Runnable cur = (i == (numThreads - 1)) ? new PrimaryWorker(prev) : new GroupWorker(prev);
            threads[i] = threadFactory.newThread(cur);
            threads[i].setDaemon(true); // just in case.
            prev = (i == (numThreads - 1)) ? null : (GroupWorker)cur;
        }
        Arrays.stream(threads).forEach(t -> t.start());
    }

    // this is the inner core of the runnable used in both GroupWorker and PrimaryWorker.
    // it returns true if any work was done.
    private class WorkGuts {
        protected final GroupWorker next;

        protected WorkGuts(final GroupWorker next) {
            this.next = next;
        }

        protected boolean work() throws InterruptedException {
            boolean workDone = false;
            final int numQueues = jobQueues.size();
            for(int i = 0; i < numQueues && !workDone; i++) {
                LinkedBlockingDeque<Runnable> cur = null;
                try {
                    cur = jobQueues.takeFirst();
                    final Runnable job = cur == null ? null : cur.poll();
                    if(job != null) {
                        workDone = true;
                        final GroupWorker n = next;
                        if(n != null) {
                            synchronized(n.waiter) {
                                n.waiter.notify();
                            }
                        }

                        job.run();
                        break;
                    }
                } finally {
                    if(cur != null)
                        jobQueues.putLast(cur);
                }
            }
            return workDone;
        }
    }

    private class PrimaryWorker extends WorkGuts implements Runnable {

        public PrimaryWorker(final GroupWorker next) {
            super(next);
        }

        @Override
        public void run() {
            try {
                startingLatch.countDown();
                while(!stop.get()) {
                    try {
                        if(!work())
                            Thread.sleep(1);
                    } catch(final RuntimeException rte) {
                        LOGGER.info("Job threw exception", rte);
                    } catch(final InterruptedException ie) {
                        if(!stop.get())
                            LOGGER.warn("Worker interrupted but we're not stopping.");
                    }
                }

                if(gracefullShutdown.get()) {
                    // wait for the other threads to shut down
                    while(runningNonPrimaryThreads.get() != 0)
                        Thread.yield();
                    // let any remaining jobs execute
                    int pollTries = 0;
                    for(final var curQueue: jobQueues) {
                        for(boolean done = false; !done;) {
                            try {
                                final Runnable job = curQueue.poll();
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
                }

            } catch(final RuntimeException rte) {
                LOGGER.error("Catastrohpic exception in runner. Thread died", rte);
                throw rte;
            } catch(final Throwable th) {
                LOGGER.error("Catastrohpic exception in runner. Thread died", th);
                throw new RuntimeException(th);
            } finally {
                LOGGER.info("Shutting down PRIMARY worker thread from pool");
            }
        }
    }

    private class GroupWorker extends WorkGuts implements Runnable {

        private final Object waiter = new Object();

        private GroupWorker(final GroupWorker next) {
            super(next);
        }

        @Override
        public void run() {
            try {
                runningNonPrimaryThreads.getAndIncrement();

                boolean workDone = false;
                startingLatch.countDown();
                while(!stop.get()) {
                    try {
                        if(!workDone) {
                            synchronized(waiter) {
                                waiter.wait();
                            }
                        }

                        workDone = work();
                    } catch(final RuntimeException rte) {
                        LOGGER.info("Job threw exception", rte);
                    } catch(final InterruptedException ie) {
                        if(!stop.get())
                            LOGGER.warn("Worker interrupted but we're not stopping.");
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
                runningNonPrimaryThreads.decrementAndGet();
            }
        }
    }
}
