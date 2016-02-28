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

package net.dempsy.serialization;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import net.dempsy.utils.test.ConditionPoll.Condition;

public abstract class TestSerializerImplementation {
    private static final int TEST_NUMBER = 42;
    private static final String TEST_STRING = "life, the universe and everything";
    private static final long baseTimeoutMillis = 20000;
    private static final int numThreads = 5;

    protected final Serializer underTest;
    protected final boolean requiresDefaultConstructor;

    protected TestSerializerImplementation(final Serializer underTest, final boolean requiresDefaultConstructor) {
        this.underTest = underTest;
        this.requiresDefaultConstructor = requiresDefaultConstructor;
    }

    protected TestSerializerImplementation(final Serializer underTest) {
        this(underTest, false);
    }

    private final MockClass o1 = new MockClass(TEST_NUMBER, TEST_STRING);

    @Test
    public void testJavaSerializeDeserialize() throws Throwable {
        runSerializer(underTest);
    }

    protected void runSerializer(final Serializer serializer) throws Throwable {
        final byte[] data = serializer.serialize(o1);
        assertNotNull(data);
        final MockClass o2 = serializer.deserialize(data, MockClass.class);
        assertNotNull(o2);
        assertEquals(o1, o2);
    }

    @Test
    public void testMultithreadedSerialization() throws Throwable {
        final Thread[] threads = new Thread[numThreads];

        final Object latch = new Object();
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean failed = new AtomicBoolean(false);
        final AtomicBoolean[] finished = new AtomicBoolean[numThreads];
        final AtomicLong[] counts = new AtomicLong[numThreads];
        final long maxSerialize = 100000;

        try {
            for (int i = 0; i < threads.length; i++) {

                finished[i] = new AtomicBoolean(true);
                counts[i] = new AtomicLong(0);

                final int curIndex = i;

                final Thread t = new Thread(new Runnable() {
                    int index = curIndex;

                    @Override
                    public void run() {
                        try {
                            synchronized (latch) {
                                finished[index].set(false);
                                latch.wait();
                            }

                            while (!done.get()) {
                                final MockClass o = new MockClass(index, "Hello:" + index);
                                final byte[] data = underTest.serialize(o);
                                final MockClass dser = underTest.deserialize(data, MockClass.class);
                                assertEquals(o, dser);
                                counts[index].incrementAndGet();
                            }
                        } catch (final Throwable th) {
                            failed.set(true);
                        } finally {
                            finished[index].set(true);
                        }
                    }
                }, "Serializer-Test-Thread-" + i);

                t.setDaemon(true);
                t.start();
                threads[i] = t;
            }

            // wait until all the threads have been started.
            assertTrue(poll(baseTimeoutMillis, finished, new Condition<AtomicBoolean[]>() {
                @Override
                public boolean conditionMet(final AtomicBoolean[] o) throws Throwable {
                    for (int i = 0; i < numThreads; i++)
                        if (o[i].get())
                            return false;
                    return true;
                }
            }));

            Thread.sleep(10);

            synchronized (latch) {
                latch.notifyAll();
            }

            // wait until so many message have been serialized
            // This can be slow on cloudbees servers so we're going to double the wait time.
            assertTrue(poll(baseTimeoutMillis * 2, counts, new Condition<AtomicLong[]>() {
                @Override
                public boolean conditionMet(final AtomicLong[] cnts) throws Throwable {
                    for (int i = 0; i < numThreads; i++)
                        if (cnts[i].get() < maxSerialize)
                            return false;
                    return true;
                }

            }));
        } finally {
            done.set(true);
        }

        for (int i = 0; i < threads.length; i++)
            threads[i].join(baseTimeoutMillis);

        for (int i = 0; i < threads.length; i++)
            assertTrue(finished[i].get());

        assertTrue(!failed.get());
    }

    @SuppressWarnings("serial")
    public static class Mock2 implements Serializable {
        private final int i;
        private final MockClass mock;

        public Mock2() {
            i = 5;
            mock = null;
        }

        public Mock2(final int i, final MockClass mock) {
            this.i = i;
            this.mock = mock;
        }

        public int getInt() {
            return i;
        }

        public MockClass getMockClass() {
            return mock;
        }

        @Override
        public boolean equals(final Object obj) {
            final Mock2 o = (Mock2) obj;
            return o.i == i && mock.equals(o.mock);
        }
    }

    @SuppressWarnings("serial")
    public static class Mock3 extends Mock2 {
        public int myI = -1;
        // UUID has no default constructor.
        private final UUID uuid = UUID.randomUUID();

        public Mock3() {}

        public Mock3(final int i, final MockClass mock) {
            super(i, mock);
            this.myI = i;
        }

        @Override
        public boolean equals(final Object obj) {
            final Mock3 o = (Mock3) obj;
            return super.equals(obj) && myI == o.myI && uuid.equals(o.uuid);
        }

        public UUID getUUID() {
            return uuid;
        }
    }

    @Test
    public void testWithFinalFields() throws Throwable {
        final Mock2 o = new Mock2(1, new MockClass(2, "Hello"));
        final byte[] data = underTest.serialize(o);
        System.out.println(new String(data));
        final Mock2 o2 = underTest.deserialize(data, Mock2.class);
        assertEquals(1, o2.getInt());
        assertEquals(new MockClass(2, "Hello"), o2.getMockClass());
    }

    @Test
    public void testChildClassSerialization() throws Throwable {
        if (!requiresDefaultConstructor) {
            final Mock2 o = new Mock3(1, new MockClass(2, "Hello"));
            final byte[] data = underTest.serialize(o);
            final Mock2 o2 = underTest.deserialize(data, Mock2.class);
            assertEquals(1, o2.getInt());
            assertEquals(new MockClass(2, "Hello"), o2.getMockClass());
            assertTrue(o2 instanceof Mock3);
            assertEquals(1, ((Mock3) o2).myI);
        }
    }

}
