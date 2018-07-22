/*
 * Copyright 2012 the original author or authors.
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

package net.dempsy.ringbuffer;

import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * <p>
 * This is a helper class for managing a set of {@link RingBufferControl}s for use
 * in a "multiple-publisher to single-consumer" thread configuration. Performance
 * comparisons to LMAX-exchanges's Disruptor using a Multi-publisher, single- consumer
 * configuration using their own 3-to-1 test seem to indicate about a 15x performance
 * increase. However, keep in mind that this is a substantially different means of
 * implementing a multi-publisher to single-consumer than the Disruptor's implementation,
 * and can therefore have substantially different behavior. Most obviously this will be
 * related to message ordering in which this implementation is substantially more likely
 * to create out of order messages between different publishers.
 * </p>
 */
public class RingBufferControlMultiplexor {
   /**
    * The number of spin tried before the {@link RingBufferControlMultiplexor#availableTo()} backs down to using
    * {@link Thread#yield()}.
    */
   public final static int SPIN_TRIES = 1000;

   private final RingBufferControl[] ringBuffers;
   private final int numOfPublishers;
   private final int indexMask;

   private long stopCount = 0L;
   private int curIndex = -1;
   private RingBufferControl curRingBuffer;

   /**
    * Instantiate a {@link RingBufferControlMultiplexor} with a given number of publishers and a given buffer size for
    * each {@link RingBufferControl}.
    * 
    * @param numOfPublishers
    *           is the number of publishers
    * @param sizePowerOfTwo
    *           is the size of each {@link RingBufferControl} and it must be a power of 2 or an
    *           {@link IllegalArgumentException} is thrown.
    * @throws IllegalArgumentException
    *            if the sizePowerOfTwo parameter isn't a power of 2.
    *            if the numOfPublishers is zero
    */
   public RingBufferControlMultiplexor(final int numOfPublishers, final int sizePowerOfTwo)
         throws IllegalArgumentException {
      if(numOfPublishers == 0)
         throw new IllegalArgumentException("Cannot create a " + RingBufferControlMultiplexor.class.getSimpleName() + " with zero publishers.");
      this.ringBuffers = new RingBufferControl[numOfPublishers];
      for(int i = 0; i < numOfPublishers; i++)
         ringBuffers[i] = new RingBufferControl(sizePowerOfTwo);
      this.numOfPublishers = numOfPublishers;
      this.indexMask = sizePowerOfTwo - 1;
   }

   /**
    * This will retrieve the {@link RingBufferControl} that corresponds to the index given. This is the way a particular
    * publisher should retrieve its corresponding {@link RingBufferControl}.
    */
   public RingBufferControl get(final int index) {
      return ringBuffers[index];
   }

   /**
    * <p>
    * {@link RingBufferControlMultiplexor#availableTo()} is a consumer
    * side call that will block until a value (or* several) have been
    * published to at least one of the underlying {@link RingBufferControl}s.
    * It will return the sequence that represents the position the publisher
    * has published to.
    * </p>
    * 
    * <p>
    * Upon returning a valid sequence the internal state will be set so that
    * the consumer can retrieve information and resources about which
    * {@link RingBufferControl} can be retrieved using
    * {@link RingBufferControlMultiplexor#getCurrentIndex()} and
    * {@link RingBufferControlMultiplexor#getCurrentControlled()}, which should only
    * be called from the consumer side once a normal sequence has been
    * returned.
    * </p>
    * 
    * <p>
    * This method can return {@link RingBufferControl#ACQUIRE_STOP_REQUEST} which
    * means the publisher has called {@link RingBufferControl#publishStop()} on ALL
    * of the {@link RingBufferControl}s being managed by this instance. Once the
    * consumer receives this value all of the managed {@link RingBufferControl} have
    * been reset.
    * </p>
    * 
    * <p>
    * If this method returns a valid sequence, it MUST NOT be called again prior to
    * calling {@link RingBufferControlMultiplexor#notifyProcessed()}
    * </p>
    * 
    * <p>
    * Note, the wait strategy uses a spin that quickly
    * ({@link RingBufferControlMultiplexor#SPIN_TRIES} iterations) backs down to using
    * {@link Thread#yield()}.
    * </p>
    */
   public long availableTo() {
      long countdown = SPIN_TRIES;
      while(true) {
         final long availableTo = tryAvailableTo();
         if(availableTo == RingBufferControl.UNAVAILABLE) {
            if(countdown > 0)
               countdown--;
            else
               Thread.yield();
            continue;
         } else
            return availableTo;
      }
   }

   /**
    * <p>
    * This method allows the consumer side to poll for publishing events on ANY of the managed
    * {@link RingBufferControl}. It will return the same thing that {@link RingBufferControlMultiplexor#availableTo()}
    * will return, but can also return {@link RingBufferControl#UNAVAILABLE} which means there's no currently published
    * value available on any underlying {@link RingBufferControl}s.
    * </p>
    * 
    * <p>
    * If this method returns a valid sequence, it MUST NOT be called again prior to calling
    * {@link RingBufferControlMultiplexor#notifyProcessed()}
    * </p>
    */
   public long tryAvailableTo() {
      while(true) {
         if((++curIndex) == numOfPublishers)
            curIndex = 0;
         final RingBufferControl rb = ringBuffers[curIndex];
         final long availableTo = rb.tryAvailableTo();
         if(availableTo == RingBufferControl.UNAVAILABLE)
            return availableTo;
         if(availableTo == RingBufferControl.ACQUIRE_STOP_REQUEST) {
            stopCount++;
            if(stopCount == numOfPublishers) {
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
    * Given that the {@link RingBufferControlMultiplexor#availableTo()} or
    * {@link RingBufferControlMultiplexor#tryAvailableTo()} has returned a valid sequence, this method will return the
    * current
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
    * This method must be called by the consumer once the consumer is finished with the currently published results. The
    * value provided should be the value returned from
    * {@link RingBufferControlMultiplexor#availableTo()} or {@link RingBufferControlMultiplexor#tryAvailableTo()},
    * however, IT MUST NOT BE {@link RingBufferControl#UNAVAILABLE} or
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
      return (int)sequence & indexMask;
   }

   public static class Manager<T> {
      private final T[][] data;
      private final RingBufferControlMultiplexor rbm;
      private final long[] previousIndexAvailable;

      // This creates a sequence number for each pattern finder
      private final AtomicInteger publisherIndexGenerator = new AtomicInteger(0);

      @SuppressWarnings("unchecked")
      public Manager(final Class<T> clazz, final RingBufferControlMultiplexor rbm) {
         this.rbm = rbm;
         final int numPublishers = rbm.numOfPublishers;
         data = (T[][])Array.newInstance(clazz, numPublishers, rbm.indexMask + 1);
         previousIndexAvailable = new long[numPublishers];
      }

      public long tryGet(final Consumer<T> consumer) {
         // get the next frames that (might) be available.
         final long binId = rbm.tryAvailableTo();
         if(RingBufferControl.ACQUIRE_STOP_REQUEST == binId)
            return binId;
         if(RingBufferControl.UNAVAILABLE != binId) {
            final int publisherIndex = rbm.getCurrentIndex();
            final long prevLocation = previousIndexAvailable[publisherIndex];
            for(long i = prevLocation; i < binId; i++) {
               final int availableIndex = rbm.index(i);
               consumer.accept(data[publisherIndex][availableIndex]);
            }
            rbm.notifyProcessed();
            previousIndexAvailable[publisherIndex] = binId;
            return binId - prevLocation;
         } else
            return 0;
      }

      public long get(final Consumer<T> consumer) {
         // get the next frames that (might) be available.
         final long binId = rbm.availableTo();
         if(RingBufferControl.ACQUIRE_STOP_REQUEST == binId)
            return binId;
         if(RingBufferControl.UNAVAILABLE != binId) {
            final int publisherIndex = rbm.getCurrentIndex();
            final long prevLocation = previousIndexAvailable[publisherIndex];
            for(long i = prevLocation; i < binId; i++) {
               final int availableIndex = rbm.index(i);
               consumer.accept(data[publisherIndex][availableIndex]);
            }
            rbm.notifyProcessed();
            previousIndexAvailable[publisherIndex] = binId;
            return binId - prevLocation;
         } else
            return 0;
      }

      public static class PublisherWithData<T> {
         public final RingBufferControl pub;
         public final T[] data;
         public final int publisherIndex;

         private PublisherWithData(final T[] data, final RingBufferControl pub, final int publisherIndex) {
            this.pub = pub;
            this.data = data;
            this.publisherIndex = publisherIndex;
         }

         public void publish(final T newEntry) {
            final long binId = pub.claim(1);
            final int collectionIndex = pub.index(binId);
            data[collectionIndex] = newEntry;
            pub.publish(binId);
         }
      }

      public PublisherWithData<T> getNextPublisher() {
         final int pubIndex = publisherIndexGenerator.getAndIncrement();
         return new PublisherWithData<T>(data[pubIndex], rbm.get(pubIndex), pubIndex);
      }
   }
}
