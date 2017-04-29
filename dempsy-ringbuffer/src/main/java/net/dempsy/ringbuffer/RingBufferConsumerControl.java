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

import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.util.PaddedLong;

/**
 * <p>
 * This code is substantially based on the ingenious work done by Martin Thompson
 *  on what he calls "Mechanical Sympathy." It leans heavily on the source code 
 *  from version 3.0.0.beta2 of the LMAX-exchange Disruptor but has been completely
 *   refactored in order to invert separate the control mechanism from what is being 
 *   controlled and to simplify the API.
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
 * Employ Martin Thompson's "mechanical sympathy" to the concurrency control mechanism
 *  for a ring buffer. It directly borrows from their code base and as of the time this
 *  class was written, it has about 20-30% more throughput than their own ring buffer as 
 *  measured by their own OneToOne benchmark test and about 15x faster as measured on 
 *  their ThreeToOne test (on my machine, 64bit Linux, 64bit Java 6 Jvm).
 * </p>
 * 
 * <p>
 * This class is incredibly temperamental and must strictly be used the way it was intended.
 *  Misuse can easily lead to lockups, missed sequences, etc.
 * </p>
 * 
 * <p>
 * In general, the {@link RingBufferControl} is the logic that controls the entries in a
 *  ring buffer, but not the ring buffer itself. These classes make no assumptions about
 *  where the data is stored (other than in physical memory), or what type it is.
 * </p>
 * 
 * <p>
 * The {@link RingBufferControl} is completely analogous to a traditional "condition 
 * variable." Just like a Condition Variable is the synchronization mechanism that gates
 * concurrent access to some 'condition', but says nothing about what the 'condition' 
 * actually is, the {@link RingBufferControl} gates concurrent access to the publishing 
 * and consuming of data in a ring buffer.
 * </p>
 * 
 * <p>
 * The 'consumer side' control and the 'publishing side' control are broken into two separate classes. This class represents control of the consumer side of the ring buffer. while {@link RingBufferControl}
 * additionally contains the publish side control.
 * </p>
 * 
 * <p>
 * These two base primitives can only be used with one consuming thread and one publishing thread, however, they form the building blocks for several other configurations (see
 * {@link RingBufferControlMulticaster} and {@link RingBufferControlMultiplexor})
 * </p>
 */
public abstract class RingBufferConsumerControl {
    /** Set to -1 as sequence starting point */
    protected static final long INITIAL_CURSOR_VALUE = -1L;

    /**
     * This value can be returned from {@link RingBufferControl#availableTo()} or {@link RingBufferControl#tryAvailableTo()} to inform the consumer that the published has called
     * {@link RingBufferControl#publishStop()}
     */
    public static final long ACQUIRE_STOP_REQUEST = -2L;

    /**
     * This value can be returned from {@link RingBufferControl#tryAvailableTo()} to indicate that there are no pending published values available.
     */
    public static final long UNAVAILABLE = -1L;

    /**
     * This interface can be implemented to provide various custom wait strategies for the consumer side of the control.
     */
    public static interface ConsumerWaitStrategy {
        public long waitFor(final long sequence, final Sequence cursor);
    }

    /**
     * Using this strategy provides 'spin' approach to waiting for data to be available for the consumer.
     */
    public static final ConsumerWaitStrategy spin = new ConsumerWaitStrategy() {
        @Override
        public long waitFor(final long sequence, final Sequence cursor) {
            long availableSequence;
            while ((availableSequence = cursor.get()) < sequence);
            return availableSequence;
        }
    };

    /**
     * Using this strategy provides 'yield' approach to waiting for data to be available for the consumer. It starts by spinning but quickly backs off to yielding the thread in between polls.
     */
    public static final ConsumerWaitStrategy yield = new ConsumerWaitStrategy() {
        private static final int SPIN_TRIES = 100;

        @Override
        public long waitFor(final long sequence, final Sequence cursor) {
            long availableSequence;
            int counter = SPIN_TRIES;
            while ((availableSequence = cursor.get()) < sequence) {
                if (counter > 0)
                    counter--;
                else Thread.yield();
            }
            return availableSequence;
        }
    };

    protected final int bufferSize;
    protected final int indexMask;
    protected final ConsumerWaitStrategy waitStrategy;

    protected final Sequence publishCursor;

    // This is updated from the consumer side, only read on the published side but only
    // when the tail cache indicates the buffer is full and only to update the cache
    protected final Sequence tail = new Sequence(INITIAL_CURSOR_VALUE);

    // Accessed ONLY on the consumer side.
    private final PaddedLong consumerTailCache = new PaddedLong(INITIAL_CURSOR_VALUE);

    private final PaddedLong headCache = new PaddedLong(INITIAL_CURSOR_VALUE);

    // This value holds this consumers former return value for a call to
    // either availableTo or tryAvailableTo. This is then used in the notifyProcessed.
    private final PaddedLong previousAvailableToResult = new PaddedLong(INITIAL_CURSOR_VALUE);

    // private final Object obj0 = null;

    // stop flag. Will contain the sequence of the stop command.
    // This value is potentially interlocked.
    protected final PaddedLong stop;
    protected boolean stopIsCommon;

    /**
     * Making this public avoids it being optimized away
     */
    public volatile long p1, p2, p3, p4, p5, p6 = 7L;

    protected RingBufferConsumerControl(final int sizePowerOfTwo,
            final ConsumerWaitStrategy waitStrategy, final Sequence cursor)
            throws IllegalArgumentException {
        this(sizePowerOfTwo, waitStrategy, cursor, new PaddedLong(Long.MAX_VALUE));
        stopIsCommon = false;
    }

    protected RingBufferConsumerControl(final int sizePowerOfTwo,
            final ConsumerWaitStrategy waitStrategy, final Sequence cursor, final PaddedLong commonStop)
            throws IllegalArgumentException {
        if (Integer.bitCount(sizePowerOfTwo) != 1)
            throw new IllegalArgumentException("bufferSize must be a power of 2");

        this.bufferSize = sizePowerOfTwo;
        this.indexMask = sizePowerOfTwo - 1;
        this.waitStrategy = waitStrategy;
        this.publishCursor = cursor;
        this.stop = commonStop;
        this.stopIsCommon = true;
    }

    /**
     * <p>
     * {@link RingBufferControl#availableTo()} is a consumer side call that will block using the wait strategy until a value (or several) have been published. It will return the sequence that represents the
     * position the publisher has published to.
     * </p>
     * 
     * <p>
     * This method can return {@link RingBufferControl#ACQUIRE_STOP_REQUEST} which means the publisher has called {@link RingBufferControl#publishStop()}. Once the consumer receives this value the
     * {@link RingBufferControl} has been reset.
     * </p>
     */
    public long availableTo() {
        return availableTo(consumerTailCache.get() + 1L);
    }

    /**
     * <p>
     * This method allows the consumer side to poll for publishing events. It will return what {@link RingBufferControl#availableTo()} will return but can also return {@link RingBufferControl#UNAVAILABLE} which
     * means there's no currently published value available
     * </p>
     */
    public long tryAvailableTo() {
        return tryAvailableTo(consumerTailCache.get() + 1L);
    }

    /**
     * This method must be called by the consumer once the consumer is finished with the currently published results.
     */
    public void notifyProcessed() {
        doNotifyProcessed(previousAvailableToResult.get());
    }

    /**
     * This method will convert the sequence to an index of a ring buffer. ring buffer sold separately.
     */
    public int index(final long sequence) {
        return (int) sequence & indexMask;
    }

    @SuppressWarnings("rawtypes")
    protected Iterator iter = null;

    public <T> Iterator<T> consumeAsIterator(final T[] values) {
        if (iter == null)
            iter = new Iterator<T>() {
                long availableTo = -1;
                long curPos = 0;

                @Override
                public boolean hasNext() {
                    if (curPos > availableTo)
                        availableTo = availableTo();
                    return availableTo != RingBufferControl.ACQUIRE_STOP_REQUEST && curPos <= availableTo;
                }

                @Override
                public T next() {
                    final T ret = values[index(curPos++)];
                    if (curPos > availableTo)
                        notifyProcessed();
                    return ret;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };

        @SuppressWarnings("unchecked")
        final Iterator<T> ret = iter;
        return ret;
    }

    public <T> Iterable<T> consumeAsIterable(final T[] values) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return consumeAsIterator(values);
            }

        };
    }

    /**
     * Once the publisher calls {@link RingBufferControl#publishStop()} and the consumer acquires it this method will return <code>true</code>. It will also return <code>true</code> up until the first sequence
     * is retrieved by a consumer. It will return <code>false</code> at all other times.
     */
    public boolean isShutdown() {
        return tail.get() == INITIAL_CURSOR_VALUE;
    }

    public long sumPaddingToPreventOptimisation() {
        return p1 + p2 + p3 + p4 + p5 + p6;
    }

    protected Sequence getTail() {
        return tail;
    }

    protected void clear() {
        if (isShutdown())
            return;

        // if the stop is common then we don't clear it here.
        if (!stopIsCommon)
            stop.set(Long.MAX_VALUE);

        // this final set has the correct memory barrier so that the
        // publish side can see that it's been shut down. So we do it last.
        tail.set(INITIAL_CURSOR_VALUE);

        // This is all consumer side
        consumerTailCache.set(INITIAL_CURSOR_VALUE);
        headCache.set(INITIAL_CURSOR_VALUE);
        previousAvailableToResult.set(INITIAL_CURSOR_VALUE); // reset the previousAvailableToResult
        iter = null;
    }

    protected long tryAvailableTo(final long requestedSequence) {
        final long lastKnownHead = headCache.get();
        if (lastKnownHead >= requestedSequence)
            return lastKnownHead;

        final long availableSequence = publishCursor.get();
        if (availableSequence < requestedSequence) {
            headCache.set(availableSequence);
            return UNAVAILABLE;
        }

        return doAvailableTo(availableSequence, requestedSequence);
    }

    protected long availableTo(final long requestedSequence) {
        final long lastKnownHead = headCache.get();
        if (lastKnownHead >= requestedSequence)
            return lastKnownHead;

        return doAvailableTo(waitStrategy.waitFor(requestedSequence, publishCursor), requestedSequence);
    }

    protected final void doNotifyProcessed(final long sequence) {
        tail.set(sequence);
        consumerTailCache.set(sequence);
    }

    private final long doAvailableTo(final long availableSequence, final long requestedSequence) {
        if (stop.get() <= availableSequence) { // the lt part of this is for the Worker impl.
            // this is a rare condition (compared to the else clause).

            // if we stopped but there's still values in the queue
            // then pass back everything but the stop, we'll get that
            // next time around.
            if (stop.get() > requestedSequence) {
                final long ret1 = stop.get() - 1L;
                previousAvailableToResult.set(ret1);
                return ret1;
            }

            doNotifyProcessed(availableSequence);
            clear();
            return ACQUIRE_STOP_REQUEST;
        } else {
            previousAvailableToResult.set(availableSequence);
            headCache.set(availableSequence);
            return availableSequence;
        }
    }

}
