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

package net.dempsy.ringbuffer;

/**
 * <p>
 * This is a helper class for managing a set of {@link RingBufferControl}s for use in a "multiple-publisher to single-consumer" thread configuration. Performance comparisons to LMAX-exchanges's Disruptor using
 * a Multi-publisher, single- consumer configuration using their own 3-to-1 test seem to indicate about a 15x performance increase. However, keep in mind that this is a substantially different means of
 * implementing a multi-publisher to single-consumer than the Disruptor's implementation, and can therefore have substantially different behavior. Most obviously this will be related to message ordering in
 * which this implementation is substantially more likely to create out of order messages between different publishers.
 * </p>
 */
public class RingBufferControlMultiplexor {
    /**
     * The number of spin tried before the {@link RingBufferControlMultiplexor#availableTo()} backs down to using {@link Thread#yield()}.
     */
    public final static int SPIN_TRIES = 1000;

    private final RingBufferControl[] ringBuffers;
    private final int numOfPublishers;
    private final int indexMask;
    private final Object[] controlled;

    private long stopCount = 0L;
    private int curIndex = -1;
    private RingBufferControl curRingBuffer;

    /**
     * Instantiate a {@link RingBufferControlMultiplexor} with a given number of publishers and a given buffer size for each {@link RingBufferControl}.
     * 
     * @param numOfPublishers
     *            is the number of publishers
     * @param sizePowerOfTwo
     *            is the size of each {@link RingBufferControl} and it must be a power of 2 or an {@link IllegalArgumentException} is thrown.
     * @throws IllegalArgumentException
     *             if the sizePowerOfTwo parameter isn't a power of 2.
     */
    public RingBufferControlMultiplexor(final int numOfPublishers, final int sizePowerOfTwo)
            throws IllegalArgumentException {
        this(numOfPublishers, sizePowerOfTwo, new Object[numOfPublishers]);
    }

    /**
     * Instantiate a {@link RingBufferControlMultiplexor} with a given set of actual buffers being controlled and a given buffer size for each {@link RingBufferControl}. The {@link RingBufferControlMultiplexor}
     * can help a consumer manage the resources that each {@link RingBufferControl} controls. After the consumer calls {@link RingBufferControlMultiplexor#availableTo()} or
     * {@link RingBufferControlMultiplexor#tryAvailableTo()} the specific resource that corresponds with the {@link RingBufferControl} that indicated available processing could be selected by a subsequent call
     * to {@link RingBufferControlMultiplexor#getCurrentControlled()}.
     * 
     * @param numOfPublishers
     *            is the number of publishers
     * @param sizePowerOfTwo
     *            is the size of each {@link RingBufferControl} and it must be a power of 2 or an {@link IllegalArgumentException} is thrown.
     * @throws IllegalArgumentException
     *             if the sizePowerOfTwo parameter isn't a power of 2.
     */
    public RingBufferControlMultiplexor(final int sizePowerOfTwo, final Object... controlledData)
            throws IllegalArgumentException {
        this(controlledData.length, sizePowerOfTwo, controlledData);
    }

    private RingBufferControlMultiplexor(final int numOfPublishers, final int sizePowerOfTwo,
            final Object[] controlledData) throws IllegalArgumentException {
        this.ringBuffers = new RingBufferControl[numOfPublishers];
        for (int i = 0; i < numOfPublishers; i++)
            ringBuffers[i] = new RingBufferControl(sizePowerOfTwo);
        this.numOfPublishers = numOfPublishers;
        this.indexMask = sizePowerOfTwo - 1;
        this.controlled = controlledData;
    }

    /**
     * This will retrieve the {@link RingBufferControl} that corresponds to the index given. This is the way a particular publisher should retrieve its corresponding {@link RingBufferControl}.
     */
    public RingBufferControl get(final int index) {
        return ringBuffers[index];
    }

    /**
     * This will retrieve the corresponding resource that's being controlled by the {@link RingBufferControl} selected by that index.
     */
    public Object getControlled(final int index) {
        return controlled[index];
    }

    /**
     * <p>
     * {@link RingBufferControlMultiplexor#availableTo()} is a consumer side call that will block until a value (or several) have been published to at least one of the underlying {@link RingBufferControl}s. It
     * will return the sequence that represents the position the publisher has published to.
     * </p>
     * 
     * <p>
     * Upon returning a valid sequence the internal state will be set so that the consumer can retrieve information and resources about which {@link RingBufferControl} can be retrieved using
     * {@link RingBufferControlMultiplexor#getCurrentIndex()} and {@link RingBufferControlMultiplexor#getCurrentControlled()}, which should only be called from the consumer side once a normal sequence has been
     * returned.
     * </p>
     * 
     * <p>
     * This method can return {@link RingBufferControl#ACQUIRE_STOP_REQUEST} which means the publisher has called {@link RingBufferControl#publishStop()} on ALL of the {@link RingBufferControl}s being managed
     * by this instance. Once the consumer receives this value all of the managed {@link RingBufferControl} have been reset.
     * </p>
     * 
     * <p>
     * If this method returns a valid sequence, it MUST NOT be called again prior to calling {@link RingBufferControlMultiplexor#notifyProcessed(long)}
     * </p>
     * 
     * <p>
     * Note, the wait strategy uses a spin that quickly ({@link RingBufferControlMultiplexor#SPIN_TRIES} iterations) backs down to using {@link Thread#yield()}.
     * </p>
     */
    public long availableTo() {
        long countdown = SPIN_TRIES;
        while (true) {
            final long availableTo = tryAvailableTo();
            if (availableTo == RingBufferControl.UNAVAILABLE) {
                if (countdown > 0)
                    countdown--;
                else Thread.yield();
                continue;
            } else return availableTo;
        }
    }

    /**
     * <p>
     * This method allows the consumer side to poll for publishing events on ANY of the managed {@link RingBufferControl}. It will return the same thing that {@link RingBufferControlMultiplexor#availableTo()}
     * will return, but can also return {@link RingBufferControl#UNAVAILABLE} which means there's no currently published value available on any underlying {@link RingBufferControl}s.
     * </p>
     * 
     * <p>
     * If this method returns a valid sequence, it MUST NOT be called again prior to calling {@link RingBufferControlMultiplexor#notifyProcessed(long)}
     * </p>
     */
    public long tryAvailableTo() {
        while (true) {
            if ((++curIndex) == numOfPublishers)
                curIndex = 0;
            final RingBufferControl rb = ringBuffers[curIndex];
            final long availableTo = rb.tryAvailableTo();
            if (availableTo == RingBufferControl.UNAVAILABLE)
                return availableTo;
            if (availableTo == RingBufferControl.ACQUIRE_STOP_REQUEST) {
                stopCount++;
                if (stopCount == numOfPublishers) {
                    stopCount = 0L;
                    return availableTo;
                }
            } else {
                curRingBuffer = rb;
                return availableTo;
            }
        }
    }

    /**
     * <p>
     * Given that the {@link RingBufferControlMultiplexor#availableTo()} or {@link RingBufferControlMultiplexor#tryAvailableTo()} has returned a valid sequence, this method will return the current
     * {@link RingBufferControl} index that the sequence is for.
     * </p>
     * 
     * <p>
     * This method's return value is only valid in the thread that called one of the {@code acquireTo} methods.
     * </p>
     */
    public int getCurrentIndex() {
        return curIndex;
    }

    /**
     * <p>
     * Given that the {@link RingBufferControlMultiplexor#availableTo()} or {@link RingBufferControlMultiplexor#tryAvailableTo()} has returned a valid sequence, this method will return the current managed
     * resource that corresponds to the {@link RingBufferControl} that has consumer data pending on it. If the {@link RingBufferControlMultiplexor} hasn't been instantiated to manage the underlying controlled
     * resources than this method will return {@code null}.
     * </p>
     * 
     * <p>
     * This method's return value is only valid in the thread that called one of the {@code acquireTo} methods.
     * </p>
     */
    public Object getCurrentControlled() {
        return controlled[curIndex];
    }

    /**
     * <p>
     * This method must be called by the consumer once the consumer is finished with the currently published results. The value provided should be the value returned from
     * {@link RingBufferControlMultiplexor#availableTo()} or {@link RingBufferControlMultiplexor#tryAvailableTo()}, however, IT MUST NOT BE {@link RingBufferControl#UNAVAILABLE} or
     * {@link RingBufferControl#ACQUIRE_STOP_REQUEST}.
     * </p>
     * 
     * <p>
     * This method MUST be called prior to a subsequent call to either of the {@code acquireTo} methods.
     * </p>
     */
    public void notifyProcessed() {
        curRingBuffer.notifyProcessed();
    }

    /**
     * This method will convert the sequence to an index of a ring buffer.
     */
    public int index(final long sequence) {
        return (int) sequence & indexMask;
    }
}
