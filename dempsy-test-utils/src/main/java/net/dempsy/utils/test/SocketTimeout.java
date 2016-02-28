package net.dempsy.utils.test;

import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Use this class with blocking IO in order to force writes or reads to throw an interrupted or socket exception once a certain amount of time has passed.
 */
public class SocketTimeout implements Runnable {
    private final Socket socket;
    private final long timeoutMillis;

    private final AtomicLong startTime = new AtomicLong(-1);
    private Thread thread = null;
    private final AtomicBoolean done = new AtomicBoolean(false);
    private boolean disrupted = false;

    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        // set the single thread's name
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("Static SocketTimeout Schedule");
            }
        });
    }

    public SocketTimeout(final Socket socket, final long timeoutMillis) {
        this.socket = socket;
        this.timeoutMillis = timeoutMillis;
        this.thread = Thread.currentThread();

        scheduler.schedule(this, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void begin() {
        startTime.set(System.currentTimeMillis());
    }

    public void end() {
        startTime.set(0);
    }

    public void stop() {
        done.set(true);
    }

    @Override
    public void run() {
        if (done.get())
            return;

        final long b = startTime.get();
        if (b != 0 && System.currentTimeMillis() - b > timeoutMillis)
            disrupt();
        else {
            long nextTimeout = (b == 0 ? timeoutMillis : (System.currentTimeMillis() - b));
            if (nextTimeout < 0L)
                nextTimeout = 1;
            scheduler.schedule(this, nextTimeout + 1, TimeUnit.MILLISECONDS);
        }
    }

    public void disrupt() {
        // we're going to kill the socket.
        try {
            thread.interrupt();
        } catch (final Throwable th) {
            System.err.println("Interrupt failed." + th);
            th.printStackTrace(System.err);
        }

        try {
            socket.close();
        } catch (final Throwable th) {
            System.err.println("Couldn't close socket." + th);
            th.printStackTrace(System.err);
        }

        disrupted = true;
    }

    /**
     * This checks, then clears, the disrupted flag.
     */
    public boolean disrupted() {
        final boolean ret = disrupted;
        disrupted = false;
        return ret;
    }
}
