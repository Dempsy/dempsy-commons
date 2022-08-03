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
import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import net.dempsy.util.executor.AsyncTaskExecutorWithMonitor;
import net.dempsy.utils.test.ConditionPoll;

public class TestAsyncTaskExecutor {

    @Test
    public void testSimple() throws Exception {
        final AtomicInteger interrupted = new AtomicInteger(0);
        try(final AsyncTaskExecutorWithMonitor ut = new AsyncTaskExecutorWithMonitor(10, "testPool", 1000);) {
            final ThreadPoolExecutor tpe = ut.getExecutor();
            final var queue = tpe.getQueue();
            for(int i = 0; i < 10; i++)
                ut.submit(() -> {
                    try {
                        Thread.sleep(10000);
                    } catch(final InterruptedException ie) {
                        interrupted.getAndIncrement();
                    }
                });

            assertEquals(10, queue.size() + tpe.getActiveCount());

            assertTrue(ConditionPoll.poll(1500, null, o -> (queue.size() + tpe.getActiveCount()) == 0));

            Thread.yield();

            assertEquals(10, interrupted.get());

            // it should be able to be reused.
            final AtomicInteger running = new AtomicInteger(0);
            for(int i = 0; i < 10; i++)
                ut.submit(() -> {
                    try {
                        running.getAndIncrement();
                        ignore(() -> Thread.sleep(1000));
                    } finally {
                        running.getAndDecrement();
                    }
                });

            assertTrue(ConditionPoll.poll(1500, null, o -> running.get() == 10));
            assertTrue(ConditionPoll.poll(1500, null, o -> running.get() == 0));
        }
    }

    @Test
    public void testSimpleIO() throws Exception {

        final AtomicBoolean serverFailed = new AtomicBoolean(false);
        final AtomicInteger count = new AtomicInteger(0);

        final Thread server = chain(new Thread(() -> {
            try(final ServerSocket serverSocket = new ServerSocket(6676);
                final Socket clientSocket = serverSocket.accept();) {

                try(final var os = clientSocket.getOutputStream();) {
                    while(true) {
                        os.write(count.getAndIncrement());
                        uncheck(() -> Thread.sleep(10));
                    }
                }
            } catch(final Exception e) {
                e.printStackTrace();
                serverFailed.set(true);
            }

        }, "testSimpleIO-server"), t -> t.setDaemon(true), t -> t.start());

        Thread.sleep(1000);

        final AtomicReference<Socket> socket = new AtomicReference<>(null);
        try(final AsyncTaskExecutorWithMonitor ut = new AsyncTaskExecutorWithMonitor(10, "testPool", 1000);) {
            final ThreadPoolExecutor tpe = ut.getExecutor();
            final var queue = tpe.getQueue();
            ut.submit(() -> ignore(() -> {
                try(Socket con = new Socket("localhost", 6676);
                    var is = con.getInputStream();) {
                    socket.set(con);
                    try {
                        while(true) {
                            if(is.read() < 0)
                                break;
                        }
                    } finally {
                        socket.set(null);
                    }
                }
            }), t -> {
                System.out.println("Closing " + socket.get());
                Optional.ofNullable(socket.get()).ifPresent(s -> uncheck(() -> s.close()));
            });

            assertEquals(1, queue.size() + tpe.getActiveCount());

            assertTrue(ConditionPoll.poll(1500, null, o -> (queue.size() + tpe.getActiveCount()) == 0));
        }

        assertTrue(count.get() > 10);

        assertTrue(ConditionPoll.poll(o -> !server.isAlive()));

    }

}
