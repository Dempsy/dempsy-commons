package net.dempsy.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import net.dempsy.utils.test.ConditionPoll;

public class TestExecutor {

    public static boolean hardcore = Boolean.parseBoolean(System.getProperty("hardcore", "false"));

    public static boolean production = Boolean.parseBoolean(System.getProperty("production", "false"));

    public static boolean fast = Boolean.parseBoolean(System.getProperty("fast", "false"));

    private final static int DEFAULT_SELECTION = 2;

    private static final int[] LOOP_COUNTS = {1000, 500, 10};
    private static final int[] ITER_COUNTS = {100000, 10000, 10};

    private static final int selection = hardcore ? 0 : (production ? 1 : (fast ? 2 : DEFAULT_SELECTION));

    private static final int loopCount = LOOP_COUNTS[selection];
    private static final int iterCount = ITER_COUNTS[selection];
    
    public static Stream<Object[]> params() {
        return Stream.of(
            new Object[] {1},
            new Object[] {10}
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testSimple(final int numThreads) throws Exception {
        for(int c = 0; c < loopCount; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final SimpleExecutor executor = new SimpleExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> executor.shutdown();) {
                for(int i = 0; i < iterCount; i++)
                    executor.submit(() -> count.incrementAndGet());
            }
            assertTrue(ConditionPoll.poll(1000, null, o -> iterCount == count.get()));
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testSimpleGroup(final int numThreads) throws Exception {
        for(int c = 0; c < loopCount; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue executor = group.newExecutor();
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> group.shutdown();) {
                for(int i = 0; i < iterCount; i++)
                    executor.submit(() -> count.incrementAndGet());
            }
            assertTrue(ConditionPoll.poll(1000, null, o -> iterCount == count.get()));
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testGroup(final int numThreads) throws Exception {
        for(int c = 0; c < loopCount; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
            for(int i = 0; i < 8; i++)
                queues[i] = group.newExecutor();
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
                for(int i = 0; i < iterCount; i++) {
                    final GroupExecutor.Queue executor = queues[i & 7];
                    executor.submit(() -> count.incrementAndGet());
                }
            }
            assertTrue(ConditionPoll.poll(o -> iterCount == count.get()));
            Thread.sleep(10);
            assertEquals(iterCount, count.get());
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testGroupLongishTasks(final int numThreads) throws Exception {
        for(int c = 0; c < 5; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
            for(int i = 0; i < 8; i++)
                queues[i] = group.newExecutor();
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
                for(int i = 0; i < iterCount; i++) {
                    final int curCount = i & 7;
                    final GroupExecutor.Queue executor = queues[curCount];
                    assertTrue(executor.submit(() -> {
                        count.incrementAndGet();
                        ignore(() -> Thread.sleep(1));
                    }));
                }
                ConditionPoll.poll(o -> iterCount == count.get());
            }
            Thread.sleep(10);
            assertEquals(iterCount, count.get());
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testGroupGracefulShutdown(final int numThreads) throws Exception {
        final AtomicLong tcount = new AtomicLong(0);
        final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
        final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
        for(int i = 0; i < 8; i++)
            queues[i] = group.newExecutor();
        final AtomicLong count = new AtomicLong();
        try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
            for(int i = 0; i < iterCount; i++) {
                final int curCount = i & 7;
                final GroupExecutor.Queue executor = queues[curCount];
                assertTrue(executor.submit(() -> {
                    count.incrementAndGet();
                    ignore(() -> Thread.sleep(1));
                }));
            }
        }
        // we poll AFTER closing
        ConditionPoll.poll(o -> iterCount == count.get());
        Thread.sleep(10);
        assertEquals(iterCount, count.get());
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testGroupOrdering(final int numThreads) throws Exception {
        for(int c = 0; c < loopCount; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor((c % 10) + 1, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
            final AtomicLong counts[] = new AtomicLong[8];
            for(int i = 0; i < 8; i++) {
                counts[i] = new AtomicLong(0);
                queues[i] = group.newExecutor();
            }

            final CountDownLatch latch = new CountDownLatch(1);
            try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
                for(int i = 0; i < (1000 * 8); i++) {
                    final int queueIndex = i & 7;
                    final int count = i;
                    final GroupExecutor.Queue executor = queues[queueIndex];
                    executor.submit(() -> {
                        // how many
                        uncheck(() -> latch.await());
                        final int curCount = (count / 8);
                        assertEquals(curCount, counts[queueIndex].get());
                        counts[queueIndex].incrementAndGet();
                    });
                }

                latch.countDown();

                for(int i = 0; i < 8; i++) {
                    final int index = i;
                    assertTrue(ConditionPoll.poll(1000, null, o -> 1000 == counts[index].get()));
                }
            }
        }
    }
}
