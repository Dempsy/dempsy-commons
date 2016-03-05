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

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.Sequence;

/**
 * <p>
 * This code is substantially based on the ingenious work done by Martin Thompson on what he calls "Mechanical Sympathy." It leans heavily on the
 * source code from version 3.0.0.beta2 of the LMAX-exchange Disruptor but has been completely refactored in order to invert separate the control
 * mechanism from what is being controlled and to simplify the API.
 * </p>
 * 
 * <p>
 * For more information on the LMAX Disruptor, see:
 * </p>
 * 
 * <pre>
 * http://lmax-exchange.github.com/disruptor/
 * </pre>
 * <hr>
 * <p>
 * Employ Martin Thompson's "mechanical sympathy" to the concurrency control mechanism for a ring buffer. It directly borrows from their code base and
 * as of the time this class was written, it has about 20-30% more throughput than their own ring buffer as measured by their own OneToOne benchmark
 * test and about 15x faster as measured on their ThreeToOne test (on my machine, 64bit Linux, 64bit Java 6 Jvm).
 * </p>
 * 
 * <p>
 * This class is incredibly temperamental and must strictly be used the way it was intended. Misuse can easily lead to lockups, missed sequences, etc.
 * </p>
 * 
 * <p>
 * In general, the {@link RingBufferControl} is the logic that controls the entries in a ring buffer, but not the ring buffer itself. These classes
 * make no assumptions about where the data is stored (other than in physical memory), or what type it is.
 * </p>
 * 
 * <p>
 * The {@link RingBufferControl} is completely analogous to a traditional "condition variable." Just like a Condition Variable is the synchronization
 * mechanism that gates concurrent access to some 'condition', but says nothing about what the 'condition' actually is, the {@link RingBufferControl}
 * gates concurrent access to the publishing and consuming of data in a ring buffer.
 * </p>
 * 
 * <p>
 * The 'consumer side' control and the 'publishing side' control are broken into two separate classes. This class represents control of the publish
 * side of the ring buffer however, it inherits from the {@link RingBufferConsumerControl} which represents the consumer side.
 * </p>
 * 
 * <p>
 * These two base primitives can only be used with one consuming thread and one publishing thread, however, they form the building blocks for several
 * other configurations (see {@link RingBufferControlMulticaster} and {@link RingBufferControlMultiplexor})
 * </p>
 */
public class RingBufferControl extends RingBufferConsumerControl
{
    @SuppressWarnings("unused")
    private static class Padding
    {
        /** Set to -1 as sequence starting point */
        public long nextValue = INITIAL_CURSOR_VALUE, tailCache = INITIAL_CURSOR_VALUE, p2, p3, p4, p5, p6, p7;
    }

    // tail cache accessed from the publish side only.
    // head cache accessed from the publish side only
    private final Padding pubHeadAndTailCache = new Padding();

    /**
     * Creates a {@link RingBufferControl} with a {@link RingBufferControl#yield} consumer
     * wait strategy.
     * 
     * @param sizePowerOfTwo is the size of the buffer being controlled. It must be a
     *            power of 2.
     * @throws IllegalArgumentException if the sizePowerOfTwo isn't a power of 2.
     */
    public RingBufferControl(final int sizePowerOfTwo) throws IllegalArgumentException
    {
        this(sizePowerOfTwo, yield);
    }

    /**
     * Creates a {@link RingBufferControl} with the given wait strategy.
     * 
     * @param sizePowerOfTwo is the size of the buffer being controlled. It must be a
     *            power of 2.
     * @param waitStrategy is the implementation of {@link RingBufferControl#waitStrategy} to use.
     * @throws IllegalArgumentException if the sizePowerOfTwo isn't a power of 2.
     */
    public RingBufferControl(final int sizePowerOfTwo, final ConsumerWaitStrategy waitStrategy)
            throws IllegalArgumentException
    {
        super(sizePowerOfTwo, waitStrategy, new Sequence(INITIAL_CURSOR_VALUE));
    }

    /**
     * This is used by the publishing thread to claim the given number of entries
     * in the buffer. The sequence returned should be supplied to the publish
     * command once the publisher thread has prepared the entries.
     * 
     * @param requestedNumberOfSlots is the number of entries in the buffer we need
     *            to wait for to be open.
     * @return the sequence to provide to the {@link RingBufferControl#publish(long)} or the {@link RingBufferControl#index(long)} methods.
     */
    public long claim(final int requestedNumberOfSlots)
    {
        final long curNextValue = pubHeadAndTailCache.nextValue;
        final long nextSequence = curNextValue + requestedNumberOfSlots;
        final long wrapPoint = nextSequence - bufferSize;
        final long cachedGatingSequence = pubHeadAndTailCache.tailCache;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > curNextValue)
        {
            long minSequence;
            while (wrapPoint > (minSequence = Math.min(tail.get(), curNextValue)))
                LockSupport.parkNanos(1L);

            pubHeadAndTailCache.tailCache = minSequence;
        }

        pubHeadAndTailCache.nextValue = nextSequence;

        return nextSequence;
    }

    /**
     * Once the publisher has readied the buffer entries that were claimed, this method
     * allows the consumer to be notified that they are ready.
     * 
     * @param sequence is the sequence returned from the {@link RingBufferControl#claim(int)} call.
     */
    public void publish(final long sequence)
    {
        publishCursor.set(sequence);
    }

    /**
     * <p>
     * The {@link RingBufferControl} can ONLY be stopped from the publish side. The publisher needs to call publishStop to stop the consumer. Once the
     * consumer reaches this point in the sequence, the consumer will receive a {@link RingBufferControl#ACQUIRE_STOP_REQUEST} returned from either
     * {@link RingBufferControl#availableTo()} or {@link RingBufferControl#tryAvailableTo()}.
     * </p>
     * 
     * <p>
     * Once that happens the {@link RingBufferControl#isShutdown()} will return <code>true</code> on both the publisher and consumer sides.
     * </p>
     * 
     * @return the sequence that represents where the consumer will be notified to stop.
     */
    public long publishStop()
    {
        final long next = claim(1);
        stop.set(next);
        publishCursor.set(next);
        return next;
    }

    @Override
    protected void clear()
    {
        if (isShutdown()) return;

        publishCursor.set(INITIAL_CURSOR_VALUE);
        pubHeadAndTailCache.tailCache = (INITIAL_CURSOR_VALUE);
        pubHeadAndTailCache.nextValue = (INITIAL_CURSOR_VALUE);

        super.clear();
    }
}
