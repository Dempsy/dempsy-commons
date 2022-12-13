package net.dempsy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class Timer {
    private final long NANOS_PER_MILLI = 1000000L;
    private final long NANOS_PER_SECOND = 1000000000L;

    private final String name;
    private final AtomicLong dur = new AtomicLong(0);
    private final AtomicLong count = new AtomicLong(0);

    private static final List<Timer> registered = new ArrayList<>();

    public Timer(final String name) {
        this.name = name;
    }

    public QuietCloseable open() {
        final long startTime = System.nanoTime();
        return () -> cap(startTime);
    }

    private void cap(final long startTime) {
        dur.addAndGet(System.nanoTime() - startTime);
        count.incrementAndGet();
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
        final long ldur = dur.get();
        final long lcount = count.get();
        return String.format("   %s - time spent millis: %d over %d calls. calls per second: %.2f",
            name, ldur / NANOS_PER_MILLI, lcount, (double)(lcount * NANOS_PER_SECOND) / ldur);
    }

    public static Timer register(final Timer timer) {
        registered.add(timer);
        return timer;
    }

    public static String displayString() {
        final StringBuilder sb = new StringBuilder();
        registered.forEach(t -> sb.append(t.toString() + System.lineSeparator()));
        return sb.toString();
    }

    public static void display() {
        registered.forEach(t -> System.out.println(t.toString()));
    }
}
