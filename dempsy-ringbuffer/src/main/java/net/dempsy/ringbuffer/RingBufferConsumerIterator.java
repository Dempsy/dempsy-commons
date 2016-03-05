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
/*
 * This code is substantially based on the ingenious work done by Martin 
 * Thompson on what he calls "Mechanical Sympathy." It leans heavily on 
 * the source code from version 3.0.0.beta2 of the LMAX-exchange Disruptor
 * but has been completely refactored in order to invert separate the control
 * mechanism from what is being controlled and to simplify the API.
 * 
 * For more information on the LMAX Disruptor, see:
 * 
 *      http://lmax-exchange.github.com/disruptor/
 */

package net.dempsy.ringbuffer;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class RingBufferConsumerIterator<T> {

    private static final AtomicLong sequence = new AtomicLong();

    private final CountDownLatch publishDone = new CountDownLatch(1);
    private final CountDownLatch allDone;
    private final Runnable publishWorker;
    private final RingBuffer<T>[] buffers;
    private final int numConsumers;

    public RingBufferConsumerIterator(final Iterator<T> delegate, final int queueSizePowerOf2, final int numConsumers) {

        this.numConsumers = numConsumers;
        allDone = new CountDownLatch(numConsumers);

        {
            @SuppressWarnings("unchecked")
            final RingBuffer<T>[] ringBuffers = new RingBuffer[numConsumers];
            buffers = ringBuffers;
        }

        for (int i = 0; i < numConsumers; i++) {
            @SuppressWarnings("unchecked")
            final T[] queue = (T[]) new Object[queueSizePowerOf2];
            buffers[i] = new RingBuffer<T>(new RingBufferControl(queueSizePowerOf2), queue);
        }

        publishWorker = new Runnable() {
            @Override
            public void run() {
                try {
                    final int numConsumersM1 = numConsumers - 1;
                    for (int index = 0; delegate.hasNext(); index++) {
                        final RingBuffer<T> r = buffers[index];
                        final RingBufferControl control = r.ringBuffer;
                        final long sequence = control.claim(1);
                        r.buffer[control.index(sequence)] = delegate.next();
                        control.publish(sequence);
                        if (index >= numConsumersM1)
                            index = -1;
                    }

                    for (int i = 0; i < numConsumers; i++)
                        buffers[i].ringBuffer.publishStop();
                } finally {
                    publishDone.countDown();
                }
            }
        };
    }

    public RingBufferConsumerIterator(final Iterator<T> delegate, final Consumer<T>[] consumers, final int queueSizePowerOf2,
            final boolean runPublishSycnronously) {
        this(delegate, queueSizePowerOf2, consumers.length);
        final int numConsumers = consumers.length;

        if (!runPublishSycnronously)
            new Thread(publishWorker, "RingBufferConsumerIterator-Producer-" + sequence.getAndIncrement()).start();

        for (int i = 0; i < numConsumers; i++) {
            final Consumer<T> consumer = consumers[i];
            final RingBuffer<T> rb = buffers[i];
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        consumer.run(rb.ringBuffer.consumeAsIterator(rb.buffer));
                    } finally {
                        allDone.countDown();
                    }
                }
            }, "RingBufferConsumerIterator-Worker-" + sequence.getAndIncrement()).start();
        }

        if (runPublishSycnronously)
            publishWorker.run();
    }

    public Iterator<T>[] getIterators() {
        @SuppressWarnings("unchecked")
        final Iterator<T>[] ret = new Iterator[numConsumers];
        int index = 0;
        for (final RingBuffer<T> rb : buffers)
            ret[index++] = rb.ringBuffer.consumeAsIterable(rb.buffer).iterator();
        return ret;
    }

    public void waitForPublishToComplete() throws InterruptedException {
        publishDone.await();
    }

    public void waitForCompletion() throws InterruptedException {
        waitForPublishToComplete();
        allDone.await();
    }

    public static interface Consumer<R> {
        public void run(Iterator<R> iter);
    }

    private static class RingBuffer<T> {
        final RingBufferControl ringBuffer;
        final T[] buffer;

        public RingBuffer(final RingBufferControl ringBuffer, final T[] buffer) {
            this.ringBuffer = ringBuffer;
            this.buffer = buffer;
        }

    }
}
