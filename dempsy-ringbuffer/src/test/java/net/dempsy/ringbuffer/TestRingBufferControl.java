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
 * http://lmax-exchange.github.com/disruptor/
 */
package net.dempsy.ringbuffer;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.lmax.disruptor.util.PaddedLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestRingBufferControl {
   private static long baseTimeoutMillis = 20000; // 20 seconds
   private static final int BUFFER_SIZE = 1024 * 8;
   private static final int MANY = 10;
   private static final int NUM_RUNS = 5;
   private static final long BASE_ITERATIONS = 1000L * 1000L;

   public static class Addup implements Runnable {
      protected Long[] events;
      protected RingBufferConsumerControl rb;
      protected final AtomicLong finalValue = new AtomicLong(0);
      protected final AtomicLong totalMessageCount = new AtomicLong(0);

      public Addup(final RingBufferConsumerControl rb, final Long[] events) {
         this.rb = rb;
         this.events = events;
      }

      public void reset() {
         finalValue.set(0L);
         totalMessageCount.set(0L);
      }

      public synchronized long getValue() {
         return finalValue.get();
      }

      @Override
      public void run() {
         long value = 0;
         long numMessages = 0;
         for(final Long v: rb.consumeAsIterable(events)) {
            value += v;
            numMessages++;
         }

         finalValue.set(value);
         totalMessageCount.set(numMessages);
      }
   }

   public static class Publisher implements Runnable {
      private final PaddedLong value = new PaddedLong(0);
      private final long[] events;
      private final RingBufferControl rbc;
      private final AtomicLong finalValue = new AtomicLong(0);
      private long iterations = -1;
      private int start = -1;

      public Publisher(final RingBufferControl rbc, final long[] events) {
         this.rbc = rbc;
         this.events = events;
      }

      public void reset(final int start, final long iterations) {
         value.set(0L);
         finalValue.set(0L);
         this.start = start;
         this.iterations = iterations;
      }

      public synchronized long getValue() {
         return finalValue.get();
      }

      @Override
      public void run() {
         final Random random = new Random();

         // scramble the data in the events
         for(int i = 0; i < BUFFER_SIZE; i++)
            events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

         long next = 0;
         final long end = start + iterations;

         for(long i = start; i < end; i++) {
            next = rbc.claim(1);
            events[rbc.index(next)] = i;
            rbc.publish(next);
         }

         rbc.publishStop();
      }
   }

   private long result(final long start, final long numTerms) {
      // heh!, Gauss was a genius. Funny, my 16 year old knew this formula off the top of his head.
      return (((start << 1) + numTerms - 1L) * numTerms) >> 1;
   }

   @Test
   public void testRingBufferControlWorkerSequencing() throws Throwable {
      System.out.println("Workers, 1-to-" + MANY + " sequencing check.");
      final Long[] events = new Long[BUFFER_SIZE];

      final Random random = new Random();
      final long iterations = BASE_ITERATIONS;

      // scramble the data in the events
      for(int i = 0; i < BUFFER_SIZE; i++)
         events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      for(int i = 0; i < NUM_RUNS; i++) {
         // select a random start point
         final int start = random.nextInt(1000);
         final long iterationsPermutation = random.nextInt(2000) - 1000;

         final long fiters = iterations + iterationsPermutation;
         final long[] values = new long[(int)fiters];

         final RingBufferControlWorkerPool rbc = new RingBufferControlWorkerPool(BUFFER_SIZE);
         final Addup[] runnables = new Addup[MANY];
         for(int j = 0; j < MANY; j++)
            runnables[j] = new Addup(null, events) {
               @Override
               public void run() {
                  this.rb = rbc.newWorker();
                  long value = 0;
                  long numMessages = 0;
                  for(long nextAvailable = rb
                        .availableTo(); (nextAvailable != RingBufferConsumerControl.ACQUIRE_STOP_REQUEST); nextAvailable = rb.availableTo()) {
                     final Long v = events[rb.index(nextAvailable)];
                     final long boxed = v;
                     values[(int)nextAvailable] = boxed;
                     value += boxed;
                     numMessages++;
                  }
                  finalValue.set(value);
                  totalMessageCount.set(numMessages);
               }
            };

         for(int j = 0; j < MANY; j++)
            runnables[j].reset();
         final long timeMillis = runRingBufferControlWorkerSequenceing(start, fiters, rbc, runnables, events);

         int index = 0;
         for(final long l: values) {
            // assertEquals(start + index, l);
            if(start + index != l)
               System.out.println("" + (start + index) + " != " + l);
            index++;
         }

         assertEquals((int)fiters, index);

         long sumMessages = 0;
         for(int j = 0; j < MANY; j++)
            sumMessages += runnables[j].getValue();
         assertEquals(result(start, fiters), sumMessages);
         System.out.format("%,d ops/sec%n", (iterations * 1000) / timeMillis);
         rbc.clear();
      }
   }

   public long runRingBufferControlWorkerSequenceing(final int start, final long iterations,
         final RingBufferControlWorkerPool rbc, final Addup[] runnables,
         final Long[] events) throws Exception {

      final long startTime = System.currentTimeMillis();
      final Thread pubThread = new Thread(new Runnable() {
         @Override
         public void run() {
            long next = 0;
            final long end = start + iterations;

            for(long i = start; i < end; i++) {
               next = rbc.next();
               events[rbc.index(next)] = i;
               rbc.publish(next);
            }

            rbc.publishStop();
         }
      });
      pubThread.start();

      final Thread[] t = new Thread[runnables.length];
      for(int i = 0; i < runnables.length; i++) {
         (t[i] = new Thread(runnables[i], "Consumer Thread for 1-to-1")).start();
         Thread.sleep(10);
      }
      Thread.yield();

      for(int i = 0; i < runnables.length; i++) {
         t[i].join(baseTimeoutMillis);
         assertFalse(t[i].isAlive());
      }
      pubThread.join();
      return System.currentTimeMillis() - startTime;
   }

   @Test
   public void test1To1RingBufferControlNoDataStop() throws Throwable {
      System.out.println("1-to-1 no data stop");

      final Long[] events = new Long[BUFFER_SIZE];

      final Random random = new Random();

      // scramble the data in the events
      for(int i = 0; i < BUFFER_SIZE; i++)
         events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      final RingBufferControl rbc = new RingBufferControl(BUFFER_SIZE);
      final Addup runnable = new Addup(rbc, events);

      runnable.reset();
      run1To1RingBufferControl(0, 0, rbc, runnable, events);
   }

   @Test
   public void test1To1RingBufferControl() throws Throwable {
      System.out.println("1-to-1");

      final Long[] events = new Long[BUFFER_SIZE];

      final Random random = new Random();
      final long iterations = BASE_ITERATIONS * 10L;

      // scramble the data in the events
      for(int i = 0; i < BUFFER_SIZE; i++)
         events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      final RingBufferControl rbc = new RingBufferControl(BUFFER_SIZE);
      final Addup runnable = new Addup(rbc, events);

      for(int i = 0; i < NUM_RUNS; i++) {
         // select a random start point
         final int start = random.nextInt(1000);
         final long iterationsPermutation = random.nextInt(2000) - 1000;

         final long fiters = iterations + iterationsPermutation;
         runnable.reset();
         final long timeMillis = run1To1RingBufferControl(start, fiters, rbc, runnable, events);
         assertEquals(result(start, fiters), runnable.getValue());
         System.out.format("%,d ops/sec%n", (iterations * 1000) / timeMillis);
      }
   }

   public long run1To1RingBufferControl(final int start, final long iterations,
         final RingBufferControl rbc, final Runnable runnable,
         final Long[] events) throws Exception {
      final Thread t = new Thread(runnable, "Consumer Thread for 1-to-1");
      t.start();
      Thread.yield();

      long next = 0;
      final long end = start + iterations;

      final long startTime = System.currentTimeMillis();
      for(long i = start; i < end; i++) {
         next = rbc.claim(1);
         events[rbc.index(next)] = i;
         rbc.publish(next);
      }

      rbc.publishStop();
      t.join(baseTimeoutMillis);
      assertFalse(t.isAlive());
      return System.currentTimeMillis() - startTime;
   }

   @Test
   public void testRingBufferControlWorker() throws Throwable {
      System.out.println("Workers, 1-to-" + MANY);
      final Long[] events = new Long[BUFFER_SIZE];

      final Random random = new Random();
      final long iterations = BASE_ITERATIONS;

      // scramble the data in the events
      for(int i = 0; i < BUFFER_SIZE; i++)
         events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      for(int i = 0; i < NUM_RUNS; i++) {
         final RingBufferControlWorkerPool rbc = new RingBufferControlWorkerPool(BUFFER_SIZE);
         final Addup[] runnables = new Addup[MANY];
         for(int j = 0; j < MANY; j++)
            runnables[j] = new Addup(null, events) {
               @Override
               public void run() {
                  this.rb = rbc.newWorker();
                  super.run();
               }
            };

         // select a random start point
         final int start = random.nextInt(1000);
         final long iterationsPermutation = random.nextInt(2000) - 1000;

         final long fiters = iterations + iterationsPermutation;
         for(int j = 0; j < MANY; j++)
            runnables[j].reset();
         final long timeMillis = runRingBufferControlWorker(start, fiters, rbc, runnables, events);
         long sumMessages = 0;
         for(int j = 0; j < MANY; j++)
            sumMessages += runnables[j].getValue();
         assertEquals(result(start, fiters), sumMessages);
         System.out.format("%,d ops/sec%n", (iterations * 1000) / timeMillis);
         rbc.clear();
      }
   }

   public long runRingBufferControlWorker(final int start, final long iterations,
         final RingBufferControlWorkerPool rbc, final Addup[] runnables,
         final Long[] events) throws Exception {

      final long startTime = System.currentTimeMillis();
      final Thread pubThread = new Thread(new Runnable() {
         @Override
         public void run() {
            long next = 0;
            final long end = start + iterations;

            for(long i = start; i < end; i++) {
               next = rbc.next();
               events[rbc.index(next)] = i;
               rbc.publish(next);
            }

            rbc.publishStop();
         }
      });
      pubThread.start();

      final Thread[] t = new Thread[runnables.length];
      for(int i = 0; i < runnables.length; i++) {
         (t[i] = new Thread(runnables[i], "Consumer Thread for 1-to-1")).start();
         Thread.sleep(10);
      }
      Thread.yield();

      for(int i = 0; i < runnables.length; i++) {
         t[i].join(baseTimeoutMillis);
         assertFalse(t[i].isAlive());
      }
      pubThread.join();
      return System.currentTimeMillis() - startTime;
   }

   @Test
   public void testRingBufferControlMulticaster() throws Throwable {
      System.out.println("Multicast, 1-to-" + MANY);
      final Long[] events = new Long[BUFFER_SIZE];

      final Random random = new Random();
      final long iterations = BASE_ITERATIONS * 10L;

      // scramble the data in the events
      for(int i = 0; i < BUFFER_SIZE; i++)
         events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      final RingBufferControlMulticaster rbc = new RingBufferControlMulticaster(MANY, BUFFER_SIZE);
      final Addup[] runnables = new Addup[MANY];
      for(int i = 0; i < MANY; i++)
         runnables[i] = new Addup(rbc.get(i), events);

      for(int i = 0; i < NUM_RUNS; i++) {
         // select a random start point
         final int start = random.nextInt(1000);
         final long iterationsPermutation = random.nextInt(2000) - 1000;

         final long fiters = iterations + iterationsPermutation;
         for(int j = 0; j < MANY; j++)
            runnables[j].reset();
         final long timeMillis = runRingBufferControlMulticaster(start, fiters, rbc, runnables, events);
         for(int j = 0; j < MANY; j++)
            assertEquals(result(start, fiters), runnables[j].getValue());
         System.out.format("%,d ops/sec%n", (iterations * 1000) / timeMillis);
      }
   }

   public long runRingBufferControlMulticaster(final int start, final long iterations,
         final RingBufferControlMulticaster rbc, final Addup[] runnables,
         final Long[] events) throws Exception {
      final Thread[] t = new Thread[runnables.length];
      for(int i = 0; i < runnables.length; i++)
         (t[i] = new Thread(runnables[i], "Consumer Thread for 1-to-1")).start();
      Thread.yield();

      long next = 0;
      final long end = start + iterations;

      final long startTime = System.currentTimeMillis();
      for(long i = start; i < end; i++) {
         next = rbc.claim(1);
         events[rbc.index(next)] = i;
         rbc.publish(next);
      }

      rbc.publishStop();
      for(int i = 0; i < runnables.length; i++) {
         t[i].join(baseTimeoutMillis);
         assertFalse(t[i].isAlive());
      }
      return System.currentTimeMillis() - startTime;
   }

   @Test
   public void testRingBufferControlMultiplexor() throws Throwable {
      System.out.println("Multiplexor, " + MANY + "-to-1");

      final long[][] events = new long[MANY][BUFFER_SIZE];

      final Random random = new Random();
      final long iterations = BASE_ITERATIONS * 10L;

      // scramble the data in the events
      for(int j = 0; j < MANY; j++)
         for(int i = 0; i < BUFFER_SIZE; i++)
            events[j][i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      final RingBufferControlMultiplexor rbc = new RingBufferControlMultiplexor(MANY, BUFFER_SIZE);
      final Publisher[] publishers = new Publisher[MANY];
      for(int i = 0; i < MANY; i++)
         publishers[i] = new Publisher(rbc.get(i), events[i]);

      for(int j = 0; j < NUM_RUNS; j++) {
         long finalValue = 0;
         long totalIterations = 0;
         for(int i = 0; i < MANY; i++) {
            // select a random start point
            final int start = random.nextInt(1000);
            final long iterationsPermutation = random.nextInt(2000) - 1000;
            final long fiters = iterations + iterationsPermutation;
            totalIterations += fiters;
            finalValue += result(start, fiters);
            publishers[i].reset(start, fiters);
         }

         final PaddedLong receievedCount = new PaddedLong(0);
         final long timeMillis = runManyTo1RingBufferControl(events, rbc, receievedCount, publishers);
         assertEquals(finalValue, receievedCount.get());
         System.out.format("%,d ops/sec%n", (totalIterations * 1000) / timeMillis);
      }
   }

   public long runManyTo1RingBufferControl(final long[][] events,
         final RingBufferControlMultiplexor rb, final PaddedLong value, final Runnable... publishers) throws Exception {
      final long[] curPoss = new long[publishers.length];
      final Thread[] t = new Thread[publishers.length];
      for(int i = 0; i < publishers.length; i++) {
         (t[i] = new Thread(publishers[i], "Producer Thread for *-to-1")).start();
         curPoss[i] = 0;
      }

      final long startTime = System.currentTimeMillis();
      while(true) {
         final long availableTo = rb.availableTo();
         if(availableTo == RingBufferControl.ACQUIRE_STOP_REQUEST)
            break;
         long curValue = value.get();
         final int curIndex = rb.getCurrentIndex();
         long curPos = curPoss[curIndex];
         while(curPos <= availableTo)
            curValue += events[rb.getCurrentIndex()][rb.index(curPos++)];
         rb.notifyProcessed();
         curPoss[curIndex] = curPos;
         value.set(curValue);
      }
      final long ret = System.currentTimeMillis() - startTime;

      for(int i = 0; i < publishers.length; i++) {
         t[i].join(baseTimeoutMillis);
         assertFalse(t[i].isAlive());
      }

      return ret;
   }

   public static class MyConsumer implements RingBufferConsumerIterator.Consumer<Long> {
      long value = 0;
      long numMessages = 0;

      @Override
      public void run(final Iterator<Long> iter) {

         while(iter.hasNext()) {
            numMessages++;
            value += iter.next();
         }
      }

      public void reset() {
         value = 0;
         numMessages = 0;
      }

   }

   @Test
   public void testRingBufferConsumerIterator() throws InterruptedException {
      System.out.println("1-to-1 using Iterator");

      final Long[] events = new Long[BUFFER_SIZE];

      final Random random = new Random();
      final long iterations = BASE_ITERATIONS * 10L;

      // scramble the data in the events
      for(int i = 0; i < BUFFER_SIZE; i++)
         events[i] = (long)random.nextInt() ^ (long)(random.nextInt() << 32);

      final MyConsumer[] consumers = new MyConsumer[MANY];

      for(int i = 0; i < MANY; i++)
         consumers[i] = new MyConsumer();

      for(int i = 0; i < NUM_RUNS; i++) {
         // select a random start point
         final long start = random.nextInt(1000);
         final long end = start + iterations;

         final long startTime = System.currentTimeMillis();
         new RingBufferConsumerIterator<Long>(new Iterator<Long>() {
            long cur = start;

            @Override
            public boolean hasNext() {
               return cur < end;
            }

            @Override
            public Long next() {
               final long ret = cur;
               cur++;
               return ret;
            }

            @Override
            public void remove() {}

         }, consumers, BUFFER_SIZE, true).waitForCompletion();

         final long timeMillis = System.currentTimeMillis() - startTime;

         long totalValue = 0;
         long totalNumMes = 0;
         for(int j = 0; j < consumers.length; j++) {
            final MyConsumer c = consumers[j];
            totalValue += c.value;
            totalNumMes += c.numMessages;
            c.reset();
         }
         assertEquals(iterations, totalNumMes);
         assertEquals(result(start, iterations), totalValue);
         System.out.format("%,d ops/sec%n", (iterations * 1000) / timeMillis);
      }

   }
}
