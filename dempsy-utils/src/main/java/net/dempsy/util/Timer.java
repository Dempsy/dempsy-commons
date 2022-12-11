package net.dempsy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Timer {
    private final long NANOS_PER_MILLI = 1000000L;
    private final long NANOS_PER_SECOND = 1000000000L;

    private final String name;
    private long startTime = 0;
    private long dur = 0;
    private long count = 0;

    private final QuietCloseable ctx = () -> cap();

    private static final List<Timer> registered = new ArrayList<>();

    public Timer(final String name) {
        this.name = name;
    }

    public QuietCloseable open() {
        startTime = System.nanoTime();
        return ctx;
    }

    public void cap() {
        dur += (System.nanoTime() - startTime);
        count++;
    }

    public <T> T time(final Supplier<T> supplied) {
        try(var cap = open();) {
            return supplied.get();
        }
    }

    public void time(final Runnable supplied) {
        try(var cap = open();) {
            supplied.run();
        }
    }

    @Override
    public String toString() {
        return String.format("   %s - time spent nanos/millis: %d/%d over %d calls. calls per second: %.2f",
            name, dur, dur / NANOS_PER_MILLI, count, (double)count * (double)NANOS_PER_SECOND / dur);
    }

    public static Timer register(final Timer timer) {
        registered.add(timer);
        return timer;
    }

    public static void display() {
        registered.forEach(t -> System.out.println(t.toString()));
    }
}
