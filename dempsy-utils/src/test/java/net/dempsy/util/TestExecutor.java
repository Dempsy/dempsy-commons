package net.dempsy.util;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import net.dempsy.utils.test.ConditionPoll;

@RunWith(Parameterized.class)
public class TestExecutor {

    @Parameters
    public static Object[][] params() {
        return new Object[][] {
            {1},
            {10},
        };
    }

    public final int numThreads;

    public TestExecutor(final int numThreads) {
        this.numThreads = numThreads;
    }

    @Test
    public void testSimple() throws Exception {
        for(int c = 0; c < 500; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final SimpleExecutor executor = new SimpleExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> executor.shutdown();) {
                for(int i = 0; i < 10000; i++)
                    executor.submit(() -> count.incrementAndGet());
            }
            assertTrue(ConditionPoll.poll(1000, null, o -> 10000 == count.get()));
        }
    }

    @Test
    public void testSimpleGroup() throws Exception {
        for(int c = 0; c < 500; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue executor = group.newExecutor();
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> group.shutdown();) {
                for(int i = 0; i < 10000; i++)
                    executor.submit(() -> count.incrementAndGet());
            }
            assertTrue(ConditionPoll.poll(1000, null, o -> 10000 == count.get()));
        }
    }

    @Test
    public void testGroup() throws Exception {
        for(int c = 0; c < 500; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
            for(int i = 0; i < 8; i++)
                queues[i] = group.newExecutor();
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
                for(int i = 0; i < 10000; i++) {
                    final GroupExecutor.Queue executor = queues[i & 7];
                    executor.submit(() -> count.incrementAndGet());
                }
            }
            assertTrue(ConditionPoll.poll(o -> 10000 == count.get()));
            Thread.sleep(10);
            assertEquals(10000, count.get());
        }
    }

    @Test
    public void testGroupLongishTasks() throws Exception {
        for(int c = 0; c < 5; c++) {
            final AtomicLong tcount = new AtomicLong(0);
            final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
            final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
            for(int i = 0; i < 8; i++)
                queues[i] = group.newExecutor();
            final AtomicLong count = new AtomicLong();
            try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
                for(int i = 0; i < 10000; i++) {
                    final int curCount = i & 7;
                    final GroupExecutor.Queue executor = queues[curCount];
                    assertTrue(executor.submit(() -> {
                        System.out.println("" + curCount + " - " + count.incrementAndGet());
                        ignore(() -> Thread.sleep(1));
                    }));
                }
                ConditionPoll.poll(o -> 10000 == count.get());
            }
            Thread.sleep(10);
            assertEquals(10000, count.get());
        }
    }

    @Test
    public void testGroupGracefulShutdown() throws Exception {
        final AtomicLong tcount = new AtomicLong(0);
        final GroupExecutor group = new GroupExecutor(numThreads, r -> new Thread(r, "testSimple-" + tcount.getAndIncrement()));
        final GroupExecutor.Queue[] queues = new GroupExecutor.Queue[8];
        for(int i = 0; i < 8; i++)
            queues[i] = group.newExecutor();
        final AtomicLong count = new AtomicLong();
        try(QuietCloseable qc = () -> uncheck(() -> group.shutdown());) {
            for(int i = 0; i < 10000; i++) {
                final int curCount = i & 7;
                final GroupExecutor.Queue executor = queues[curCount];
                assertTrue(executor.submit(() -> {
                    System.out.println("" + curCount + " - " + count.incrementAndGet());
                    ignore(() -> Thread.sleep(1));
                }));
            }
        }
        // we poll AFTER closing
        ConditionPoll.poll(o -> 10000 == count.get());
        Thread.sleep(10);
        assertEquals(10000, count.get());
    }

    @Test
    public void testGroupOrdering() throws Exception {
        for(int c = 0; c < 500; c++) {
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
