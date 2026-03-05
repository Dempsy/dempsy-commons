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

import static net.dempsy.util.Functional.chain;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TestStupidHashMap {

    public static final int NUMWRITERS = 8;
    public static final int NUMREADERS = 4;
    public static final int NUMWRITES = 50000;

    public static Stream<Object[]> params() {
        return Stream.of(
            new Object[] {(Supplier<Map<Integer, Integer>>)() -> new ConcurrentHashMap<Integer, Integer>(2048)},
            new Object[] {(Supplier<Map<Integer, Integer>>)() -> new StupidHashMap<Integer, Integer>(1)},
            new Object[] {(Supplier<Map<Integer, Integer>>)() -> new StupidHashMap<Integer, Integer>()}
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPounding(final Supplier<Map<Integer, Integer>> supplier) throws InterruptedException {
        final Map<Integer, Integer> it = supplier.get();

        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean failedWrites = new AtomicBoolean(false);
        final AtomicBoolean failedReads = new AtomicBoolean(false);
        final AtomicBoolean failedChecks = new AtomicBoolean(false);

        try {
            final Integer[] values = createUniqeSet(1000);

            assertEquals(1000, values.length);

            // preload the map
            for(int ii = 0; ii < values.length; ii++) {
                final Integer kv = values[ii];
                final Integer already = it.putIfAbsent(kv, kv);
                assertNull(already);
            }

            assertEquals(values.length, it.size());

            new Thread(() -> {
                while(!done.get()) {
                    if(it.size() != values.length)
                        failedChecks.set(true);
                }
            }, "Checker").start();

            final List<Thread> writers = IntStream.range(0, NUMWRITERS).mapToObj(i -> new Thread(() -> {
                for(int j = 0; j < NUMWRITES;) {
                    for(int ii = 0; ii < values.length; ii++) {
                        final Integer kv = values[ii];
                        if(it.putIfAbsent(kv, kv) == null) {
                            failedWrites.set(true);
                        }
                        j++;
                    }
                }
            }, "Writer-" + i)).map(t -> {
                t.start();
                return t;
            }).collect(Collectors.toList());

            IntStream.range(0, NUMREADERS).forEach(i -> new Thread(() -> {
                while(!done.get()) {
                    for(int ii = 0; ii < values.length; ii++) {
                        final Integer k = values[ii];
                        final Integer v = it.get(k);
                        if(v != null) {
                            if(k.intValue() != v.intValue())
                                failedReads.set(true);
                        }
                    }
                }
            }, "Reader-" + i).start());

            writers.forEach(t -> {
                try {
                    t.join(20000);
                    if(t.isAlive())
                        throw new IllegalStateException("Failed on writes.");
                } catch(final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

        } finally {
            done.set(true);
        }

        assertFalse(failedWrites.get());
        assertFalse(failedReads.get());
        assertFalse(failedChecks.get());

    }

    @ParameterizedTest
    @MethodSource("params")
    public void testUniqueOwnershipSimple(final Supplier<Map<Integer, Integer>> supplier) throws InterruptedException {
        final Map<Integer, Integer> it = supplier.get();

        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean gotWhenIOwnSlot = new AtomicBoolean(false);
        final AtomicBoolean gotAnother = new AtomicBoolean(false);
        final AtomicBoolean removedAnother = new AtomicBoolean(false);

        try {
            final Integer[] values = createUniqeSet(1000);

            // preload the map
            for(int ii = 0; ii < values.length; ii++) {
                final Integer kv = values[ii];
                final Integer already = it.putIfAbsent(kv, kv);
                assertNull(already);
            }

            final Integer toWorkWith = values[500];

            it.remove(toWorkWith); // initialize it as owned by the putter thread.

            final List<Thread> workers = IntStream.range(0, NUMWRITERS).mapToObj(i -> chain(new Thread(() -> {
                boolean iOwnPositionInMap = false;
                final Integer value = Integer.valueOf(i);
                int numWrites = 0;
                while(!done.get() && numWrites < NUMWRITES) {
                    if(it.putIfAbsent(toWorkWith, value) == null) {
                        // it went in. I better not already own that position.
                        if(iOwnPositionInMap)
                            gotWhenIOwnSlot.set(true);;
                        iOwnPositionInMap = true; // it's my position in the map
                    }

                    if(iOwnPositionInMap) {
                        // I should be able to retrieve it.
                        final Integer shouldBeMyNumber = it.get(toWorkWith);
                        if(shouldBeMyNumber == null || !shouldBeMyNumber.equals(value))
                            gotAnother.set(true);

                        // now let's remove it.
                        final Integer shouldBeMyNumberAgain = it.remove(toWorkWith);
                        if(shouldBeMyNumberAgain == null || !shouldBeMyNumberAgain.equals(value))
                            removedAnother.set(true);
                        iOwnPositionInMap = false;
                    }
                    numWrites++;
                }
            }, "UniqueOwnerGrabber-" + i), t -> t.start())).collect(Collectors.toList());

            join(workers, 20000);
        } finally {
            done.set(true);
        }

        assertFalse(gotWhenIOwnSlot.get(), "Got it in but already had it in.");
        assertFalse(gotAnother.get(), "Retrieved an incorrect entry when owned");
        assertFalse(removedAnother.get(), "Removed an incorrect entry when owned");
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPoundingWithRemove(final Supplier<Map<Integer, Integer>> supplier) throws InterruptedException {
        final Map<Integer, Integer> it = supplier.get();

        final List<Long> numAdded = new ArrayList<Long>();
        final List<Long> numRemoved = new ArrayList<Long>();
        for(int loop = 0; loop < 3; loop++) {
            final AtomicBoolean done = new AtomicBoolean(false);

            final AtomicBoolean failedReads = new AtomicBoolean(false);

            try {
                // make unique values
                final Integer[] values = createUniqeSet(1000);

                assertEquals(1000, values.length);

                // preload the map
                for(int ii = 0; ii < values.length; ii++) {
                    final Integer kv = values[ii];
                    final Integer already = it.putIfAbsent(kv, kv);
                    if(loop == 0)
                        assertNull(already);
                }

                numAdded.add(Long.valueOf(values.length));

                if(loop == 0)
                    assertEquals(values.length, it.size());

                final List<Thread> writers = IntStream.range(0, NUMWRITERS).mapToObj(i -> startThread("Writer-" + i, () -> {
                    long c = 0;
                    for(int j = 0; j < NUMWRITES;) {
                        for(int ii = 0; ii < values.length; ii++) {
                            final Integer kv = values[ii];
                            j++;
                            if(it.putIfAbsent(kv, kv) == null)
                                c++; // this one got on
                        }
                    }
                    synchronized(numAdded) {
                        numAdded.add(Long.valueOf(c));
                    }
                })).collect(Collectors.toList());

                final List<Thread> removers = IntStream.range(0, NUMWRITERS).mapToObj(i -> startThread("Remover-" + i, () -> {
                    int c = 0;
                    while(!done.get()) {
                        for(int ii = 0; ii < values.length; ii++) {
                            final Integer k = values[ii];
                            final Integer v = it.remove(k);
                            if(v != null) {
                                c++;
                                if(k.intValue() != v.intValue())
                                    failedReads.set(true);
                            }
                        }
                    }
                    synchronized(numRemoved) {
                        numRemoved.add(Long.valueOf(c));
                    }
                })).collect(Collectors.toList());

                done.set(true); // allow threads to exit for join

                join(writers, 20000);

                join(removers, 20000);

            } finally {
                done.set(true);
            }

            assertEquals(it.size(), sum(numAdded) - sum(numRemoved));
            assertFalse(failedReads.get());
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testUniqueOwnershipRotating(final Supplier<Map<Integer, Integer>> supplier) throws InterruptedException {
        final Map<Integer, Integer> it = supplier.get();

        for(int loop = 0; loop < 5; loop++) {
            final AtomicBoolean done = new AtomicBoolean(false);
            final AtomicBoolean gotWhenIOwnedObject = new AtomicBoolean(false);
            final AtomicBoolean gotOne = new AtomicBoolean(false);
            final List<Integer> owners = new ArrayList<>();

            final Integer[] values = createUniqeSet(1000);

            // preload the map
            for(int ii = 0; ii < values.length; ii++) {
                final Integer kv = values[ii];
                final Integer already = it.putIfAbsent(kv, kv);
                assertNull(already);
            }

            final Integer toWorkWith = values[500];

            try {
                it.remove(toWorkWith); // initialize it as owned by the putter thread.

                final List<Thread> workers = IntStream.range(0, NUMWRITERS).mapToObj(i -> chain(new Thread(() -> {
                    boolean iOwnIt = i == 0;
                    final Integer value = Integer.valueOf(i);
                    try {
                        int numWrites = 0;
                        while(!done.get() && numWrites < NUMWRITES) {
                            if(iOwnIt) {
                                // gratuitous get ... it should be null since I'm holding it.
                                final Integer fromMap = it.get(toWorkWith);
                                if(fromMap != null) { // WHAT?
                                    System.err.println("I (" + value + ") owned it but one was on the map anyway.");
                                    gotOne.set(true);
                                    break;
                                }

                                final Integer alreadyThere = it.putIfAbsent(toWorkWith, value);
                                if(alreadyThere != null) {
                                    System.out.println("I (" + value + ")  owned it but there seemed to be one " + alreadyThere + "  there already.");
                                    gotWhenIOwnedObject.set(true);
                                    break;
                                }
                                iOwnIt = false;
                            } else { // maybe I can get it
                                final Integer fromMap = it.remove(toWorkWith);
                                if(fromMap != null) { // I got it
                                    iOwnIt = true;
                                }
                            }
                            numWrites++;
                        }
                    } finally {
                        if(iOwnIt) {
                            synchronized(owners) {
                                owners.add(value);
                            }
                        }
                    }
                }, "UniqueOwnerGrabber-" + i), t -> t.start())).collect(Collectors.toList());

                join(workers, 30000);
            } finally {
                done.set(true);
            }

            assertFalse(gotWhenIOwnedObject.get(), "Got it back on an insert when I already had it.");

            assertFalse(gotOne.get(), "Retreived it when I already had it.");

            if(it.get(toWorkWith) == null) {
                assertEquals(1, owners.size(), "There seemed to be too many owners at the end " + owners);
            } else {
                assertEquals(0, owners.size(), "There seemed to be too many owners at the end " + owners);
            }
        }
    }

    private static class MutableInt {
        public Integer value = null;
    }

    public class Remover implements Runnable {
        final Map<Integer, Integer> it;
        List<Integer> removed = new ArrayList<>();
        final int i;
        final AtomicBoolean done;
        final MutableInt toRemove;

        Remover(final Map<Integer, Integer> it, final int i, final AtomicBoolean done, final MutableInt toRemove) {
            this.it = it;
            this.i = i;
            this.done = done;
            this.toRemove = toRemove;
        }

        @Override
        public void run() {
            while(!done.get()) {
                // race to remove the value
                final Integer mine = it.remove(toRemove.value);
                if(mine != null)
                    removed.add(mine);
            }
        }
    }

    public static class RunnableThread {
        public final Remover r;
        public final Thread t;

        public RunnableThread(final Remover r, final String name) {
            this.r = r;
            this.t = new Thread(r, name);
            t.start();
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testUniqueRemove(final Supplier<Map<Integer, Integer>> supplier) throws InterruptedException {
        final Map<Integer, Integer> it = supplier.get();

        final Integer[] values = createUniqeSet(1000);

        // preload the map
        for(int ii = 0; ii < values.length; ii++) {
            final Integer kv = values[ii];
            final Integer already = it.putIfAbsent(kv, kv);
            assertNull(already);
        }

        final int[] removeOrder = new int[values.length];
        for(int i = 0; i < values.length; i++) {
            final int halfi = i / 2;
            removeOrder[i] = (i & 0x1) == 0 ? halfi : (values.length - halfi - 1);
        }

        final MutableInt toRemove = new MutableInt();
        final AtomicBoolean done = new AtomicBoolean(false);

        toRemove.value = values[removeOrder[0]]; // the first one is removed when the Runnable first runs.
        final List<RunnableThread> workers = IntStream.range(0, NUMWRITERS)
            .mapToObj(i -> new RunnableThread(new Remover(it, i, done, toRemove), "UniqueRemover-" + i))
            .collect(Collectors.toList());

        try {
            // create and start removers
            for(int i = 1; i < removeOrder.length; i++) {
                final int index = i;
                assertTrue(poll(o -> it.size() == (values.length - index)));
                toRemove.value = values[removeOrder[i]];
            }
        } finally {
            // make sure all of the threads finish
            done.set(true);
            workers.forEach(r -> r.t.interrupt());
        }

        join(workers.stream().map(r -> r.t).collect(Collectors.toList()), 20000);

        // now check the results.
        final List<List<Integer>> allInts = workers.stream().map(r -> r.r.removed).collect(Collectors.toList());

        final Set<Integer> removedVals = new HashSet<>();
        int count = 0;
        // make sure they're all unique and complete.
        for(final List<Integer> cur: allInts) {
            count += cur.size();
            for(final Integer i: cur) {
                assertFalse(removedVals.contains(i));
                removedVals.add(i);
            }
        }

        assertEquals(0, it.size());
        assertEquals(values.length, count);
        final Set<Integer> originalValues = new HashSet<>(Arrays.asList(values));
        for(final Integer cur: values) {
            assertTrue(originalValues.contains(cur));
            originalValues.remove(cur);
        }

        assertTrue(originalValues.isEmpty());

    }

    private static long sum(final List<Long> vals) {
        return vals.stream().reduce(Long.valueOf(0), (v1, v2) -> Long.valueOf(v1.longValue() + v2.longValue())).longValue();
    }

    private static Thread startThread(final String name, final Runnable r) {
        final Thread ret = new Thread(r, name);
        ret.start();
        return ret;
    }

    private static void join(final Thread t, final long timeout) {
        try {
            t.join(timeout);
            if(t.isAlive())
                throw new IllegalStateException("Failed on join.");
        } catch(final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void join(final List<Thread> ts, final long timeout) {
        ts.forEach(t -> join(t, timeout));
    }

    private static final Integer[] createUniqeSet(final int size) {
        final Random random = new Random();
        // make unique values
        final Set<Integer> svals = new HashSet<>();
        while(svals.size() < size) {
            svals.add(random.nextInt());
        }
        return svals.toArray(new Integer[svals.size()]);
    }
}
