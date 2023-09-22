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
/*
 * This code is substantially based on the ingenious work done by Martin
 * Thompson on what he calls "Mechanical Sympathy." It leans heavily on
 * the source code from version 3.0.0.beta2 of the LMAX-exchange Disruptor
 * but has been completely refactored in order to invert separate the control
 * mechanism from what is being controlled and to simplify the API.
 * 
 * For more information on the LMAX Disruptor, see:
 * 
 * https://lmax-exchange.github.io/disruptor/
 */

package net.dempsy.ringbuffer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.Sequence;

import net.dempsy.ringbuffer.internal.PaddedLong;

/**
 * <p>
 * This is a helper class for managing a set of {@link RingBufferControl}s for use in a "single-publisher to
 * multi-consumer" thread configuration
 * where the consumers are workers reading from the queued data.
 * </p>
 * 
 * <p>
 * Currently it would be really bad for Worker consumers to mix availableTo and tryAvailableTo. If a worker uses
 * tryAvailableTo it MUST use
 * tryAvailableTo until tryAvailableTo returns a value that isn't RingBufferConsumerControl.UNAVAILABLE before using
 * availableTo.
 * </p>
 */
public class RingBufferControlWorkerPool {
   private final Sequence cursor = new Sequence(RingBufferConsumerControl.INITIAL_CURSOR_VALUE);
   private final Sequence workSequence = new Sequence(RingBufferConsumerControl.INITIAL_CURSOR_VALUE);

   private Sequence[] tails;

   private final int bufferSize;
   private final int indexMask;
   private final int sizePowerOfTwo;

   private final RingBufferConsumerControl.ConsumerWaitStrategy waitStrategy;
   private final PaddedLong commonStop = new PaddedLong(Long.MAX_VALUE);

   // We need to pre-allocate the first worker requested or the publisher can get ahead
   // of the workers. This will prevent that.
   private RingBufferConsumerControl firstWorker;
   private boolean firstWorkerGiven = false;

   @SuppressWarnings("unused")
   private static class Padding {
      /** Set to -1 as sequence starting point */
      public long nextValue = RingBufferConsumerControl.INITIAL_CURSOR_VALUE,
            tailCache = RingBufferConsumerControl.INITIAL_CURSOR_VALUE,
            p2, p3, p4, p5, p6, p7;
   }

   // tail cache accessed from the publish side only.
   // head cache accessed from the publish side only
   private final Padding pubHeadAndTailCache = new Padding();

   public RingBufferControlWorkerPool(final int sizePowerOfTwo) {
      this(sizePowerOfTwo, RingBufferConsumerControl.yield);
   }

   public RingBufferControlWorkerPool(final int sizePowerOfTwo,
         final RingBufferConsumerControl.ConsumerWaitStrategy waitStrategy)
         throws IllegalArgumentException {
      this.sizePowerOfTwo = sizePowerOfTwo;
      this.waitStrategy = waitStrategy;
      this.tails = new Sequence[0];

      this.indexMask = sizePowerOfTwo - 1;
      this.bufferSize = sizePowerOfTwo;

      firstWorker = newWorker(true);
   }

   private RingBufferConsumerControl createConsumerControl() {
      return new RingBufferConsumerControl(sizePowerOfTwo, waitStrategy, cursor, commonStop) {
         // save off the workSequence ... shared among all workers.
         private final Sequence workSequence = RingBufferControlWorkerPool.this.workSequence;

         @Override
         protected void clear() {
            synchronized(RingBufferControlWorkerPool.this) {
               removeWorker(this);
               super.clear();
            }
         }

         @Override
         public long availableTo() {
            // humm ... no getAndIncrement.
            final long ret = workSequence.incrementAndGet();
            super.doNotifyProcessed(ret - 1L); // notify up to the previous
            final long alt = super.availableTo(ret);
            return alt == RingBufferConsumerControl.ACQUIRE_STOP_REQUEST ? alt : ret;
         }

         final PaddedLong allocatedTry = new PaddedLong(INITIAL_CURSOR_VALUE);

         // TODO: add a test for this.
         @Override
         public long tryAvailableTo() {
            final boolean inProcess = allocatedTry.get() != INITIAL_CURSOR_VALUE;
            final long ret = inProcess ? allocatedTry.get() : workSequence.incrementAndGet();
            if(!inProcess) {
               allocatedTry.set(ret);
               super.doNotifyProcessed(ret - 1L);
            }

            final long alt = super.tryAvailableTo(ret);
            if(alt == RingBufferConsumerControl.UNAVAILABLE)
               return alt;
            else if(inProcess) // reset the allocatedTry
               allocatedTry.set(INITIAL_CURSOR_VALUE); // reset the allocatedTry
            return alt == RingBufferConsumerControl.ACQUIRE_STOP_REQUEST ? alt : ret;
         }

         @Override
         public void notifyProcessed() { /* We don't do anything here. This is done in availableTo/tryAvailableTo. */}

         /**
          * The iterator returned from this call has the peculiar property of requiring that
          * 'next()' be called if 'hasNext()' returns true or data will be lost. 'hasNext(),'
          * when it returns 'true' literally reserves the spot with the data to be retrieved.
          * Ignoring this and moving on will leave it in the worker queue unprocessed.
          */
         @Override
         public <T> Iterator<T> consumeAsIterator(final T[] values) {
            if(iter == null) iter = new Iterator<T>() {
               long availableTo = RingBufferControl.INITIAL_CURSOR_VALUE;
               boolean nextIsReady = false;

               @Override
               public boolean hasNext() {
                  if(!nextIsReady) {
                     availableTo = availableTo();
                     nextIsReady = true;
                  }
                  return availableTo != RingBufferControl.ACQUIRE_STOP_REQUEST;
               }

               @Override
               public T next() {
                  nextIsReady = false;
                  final T ret = values[index(availableTo)];
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

      };

   }

   public synchronized RingBufferConsumerControl newWorker() {
      if(!firstWorkerGiven) {
         firstWorkerGiven = true;
         return firstWorker;
      }
      return newWorker(true);
   }

   private synchronized RingBufferConsumerControl newWorker(final boolean force) {
      final RingBufferConsumerControl ret = createConsumerControl();

      // replace the tails array;
      final Sequence[] newTails = new Sequence[tails.length + 1];
      int index = 0;
      for(final Sequence tail: tails)
         newTails[index++] = tail;
      newTails[newTails.length - 1] = ret.getTail();
      tails = newTails;

      return ret;
   }

   public synchronized void removeWorker(final RingBufferConsumerControl worker) {

      // make sure the consumer is still participating
      final List<Sequence> tailsList = Arrays.asList(tails);
      if(tailsList.contains(worker.tail)) {
         // replace the tails array;
         final Sequence[] newTails = new Sequence[tails.length - 1];
         int index = 0;
         for(final Sequence tail: tails)
            if(worker.tail != tail) newTails[index++] = tail;
         tails = newTails;
      }
   }

   /**
    * This is used by the publishing thread to claim the given number of entries
    * in the buffer all of the underlying {@link RingBufferControl}s. The sequence
    * returned should be supplied to the {@link RingBufferControlWorkerPool#publish(long)} command once the publisher
    * thread has prepared the
    * entries.
    * 
    * @return the sequence to provide to the {@link RingBufferControl#publish(long)} or the
    *         {@link RingBufferControl#index(long)} methods.
    */
   public long next() {
      final long curNextValue = pubHeadAndTailCache.nextValue;
      final long nextSequence = curNextValue + 1L;
      final long wrapPoint = nextSequence - bufferSize;
      final long cachedGatingSequence = pubHeadAndTailCache.tailCache;

      if(wrapPoint > cachedGatingSequence || cachedGatingSequence > curNextValue) {
         long minSequence;
         while(wrapPoint > (minSequence = getMinimumSequence(tails, curNextValue)))
            LockSupport.parkNanos(1L);

         pubHeadAndTailCache.tailCache = minSequence;
      }

      pubHeadAndTailCache.nextValue = nextSequence;

      return nextSequence;
   }

   /**
    * This is used by the publishing thread to claim the next entry
    * in the buffer all of the underlying {@link RingBufferControl}s. The sequence
    * returned should be supplied to the {@link RingBufferControlWorkerPool#publish(long)} command once the publisher
    * thread has prepared the
    * entries.
    * 
    * If there are no currently available slots in the RingBuffer then
    * RingBufferControl.UNAVAILABLE will be returned.
    *
    * @return the sequence to provide to the {@link RingBufferControl#publish(long)} or the
    *         {@link RingBufferControl#index(long)} methods.
    */
   public long tryNext() {
      final long curNextValue = pubHeadAndTailCache.nextValue;
      final long nextSequence = curNextValue + 1L;
      final long wrapPoint = nextSequence - bufferSize;
      final long cachedGatingSequence = pubHeadAndTailCache.tailCache;

      if(wrapPoint > cachedGatingSequence || cachedGatingSequence > curNextValue) {
         final long minSequence = getMinimumSequence(tails, curNextValue);
         if(wrapPoint > minSequence) return RingBufferControl.UNAVAILABLE;

         pubHeadAndTailCache.tailCache = minSequence;
      }

      pubHeadAndTailCache.nextValue = nextSequence;

      return nextSequence;
   }

   /**
    * Once the publisher has readied the buffer entries that were claimed, this method
    * allows the subscribers to be notified that they are ready.
    * 
    * @param sequence is the sequence returned from the {@link RingBufferControlWorkerPool#next()} or
    *           {@link RingBufferControlWorkerPool#tryNext()} call.
    */
   public void publish(final long sequence) {
      cursor.set(sequence);
   }

   /**
    * <p>
    * The {@link RingBufferControl} can ONLY be stopped from the publish side. The publisher needs to call publishStop
    * to stop the consumer. Once the
    * consumer reaches this point in the sequence, the consumer will receive a
    * {@link RingBufferControl#ACQUIRE_STOP_REQUEST} returned from either
    * {@link RingBufferControl#availableTo()} or {@link RingBufferControl#tryAvailableTo()}.
    * </p>
    * 
    * <p>
    * Once that happens the {@link RingBufferControl#isShutdown()} will return <code>true</code> on both the publisher
    * and consumer sides.
    * </p>
    * 
    * @return the sequence that represents where the consumer will be notified to stop.
    */
   public long publishStop() {
      final long next = next();
      commonStop.set(next); // stop is set to where the next available location is.

      // for (int i = 1; i < tails.length; i++)
      // next = next();

      publish(Long.MAX_VALUE - 2); /// jump to the end but don't interfere with the initial value which is set to
                                   /// Long.MAX_VALUE
      return next;
   }

   /**
    * The {@link RingBufferControlWorkerPool} can be cleared or "reset" so that it can be
    * reused from scratch HOWEVER, the workers must be re-retrieved via newWorker(); none
    * of the outstanding workers can be reused. Also, if any outstanding workers are still working
    * when clear() is called they will likely deadlock.
    */
   public synchronized void clear() {
      if(tails.length != 0) throw new IllegalStateException("You cannot clear() a " + RingBufferControlWorkerPool.class.getSimpleName()
            + " with outstanding consumers.");
      cursor.set(RingBufferConsumerControl.INITIAL_CURSOR_VALUE);
      pubHeadAndTailCache.nextValue = RingBufferConsumerControl.INITIAL_CURSOR_VALUE;
      pubHeadAndTailCache.tailCache = RingBufferConsumerControl.INITIAL_CURSOR_VALUE;
      workSequence.set(RingBufferConsumerControl.INITIAL_CURSOR_VALUE);
      tails = new Sequence[0];
      commonStop.set(Long.MAX_VALUE);
      firstWorker = newWorker(true);
      firstWorkerGiven = false;
   }

   /**
    * This method will convert the sequence to an index of a ring buffer.
    */
   public int index(final long sequence) {
      return (int)sequence & indexMask;
   }

   /**
    * Once the publisher calls {@link RingBufferControl#publishStop()} and the
    * consumer acquires it this method will return <code>true</code>. It will
    * also return <code>true</code> up until the first sequence is retrieved
    * by a consumer. It will return <code>false</code> at all other times.
    */
   public boolean isShutdown() {
      return cursor.get() == RingBufferConsumerControl.INITIAL_CURSOR_VALUE;
   }

   public int getBufferSize() {
      return bufferSize;
   }

   /**
    * This is an estimate of the number of entries currently in the RingBuffer.
    */
   public long getNumEntries() {
      // If the client a worker is waiting in an availableTo call then the
      // workerSequence will be ahead of the pubHeadAndTailCache.nextValue.
      final long ret = pubHeadAndTailCache.nextValue - workSequence.get();
      return ret < 0 ? 0 : ret;
   }

   /**
    * Get the minimum sequence from an array of {@link com.lmax.disruptor.Sequence}s.
    * 
    * @param sequences to compare.
    * @param minimum an initial default minimum. If the array is empty this value will be
    *           returned.
    * @return the minimum sequence found or Long.MAX_VALUE if the array is empty.
    */
   private static long getMinimumSequence(final Sequence[] sequences, long minimum) {
      for(int i = 0, n = sequences.length; i < n; i++) {
         final long value = sequences[i].get();
         minimum = Math.min(minimum, value);
      }

      return minimum;
   }
}
