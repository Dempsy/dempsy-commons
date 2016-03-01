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

package net.dempsy.cluster;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public abstract class TestClusterImpls {
    private final ClusterInfoSessionFactory[] clusterFactories;

    protected TestClusterImpls(final ClusterInfoSessionFactory... clusterFactories) {
        this.clusterFactories = clusterFactories;
    }

    @FunctionalInterface
    private static interface Checker {
        public void check(String pass, ClusterInfoSessionFactory factory) throws Throwable;
    }

    private <T, N> void runAllCombinations(final Checker checker) throws Throwable {
        for (final ClusterInfoSessionFactory factory : clusterFactories) {

            if (checker != null)
                checker.check("pass for:" + factory.getClass().getSimpleName(), factory);

        }
    }

    public static String checkPathExists(final String path, final ClusterInfoSession session) throws ClusterInfoException {
        return session.exists(path, null) ? path : null;
    }

    public static String recurseCreate(final String path, final ClusterInfoSession session) throws ClusterInfoException {
        final String[] elements = path.substring(1).split("/");
        String cur = "";
        for (final String element : elements) {
            cur = cur + "/" + element;
            session.mkdir(cur, null, DirMode.PERSISTENT);
        }
        return cur;
    }

    @Test
    public void testMpClusterFromFactory() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app1/test-cluster";

            try (final ClusterInfoSession session = factory.createSession();) {
                assertNotNull(pass, session);
                final String clusterPath = recurseCreate(cid, session);

                assertEquals(clusterPath, cid);

                assertNotNull(pass, clusterPath);
                assertTrue(pass, session.exists(clusterPath, null));

                // there should be nothing currently registered
                final Collection<String> slots = session.getSubdirs(clusterPath, null);
                assertNotNull(pass, slots);
                assertEquals(pass, 0, slots.size());

                assertNull(pass, session.getData(clusterPath, null));
            }
        });
    }

    @Test
    public void testSimpleClusterLevelData() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app2/test-cluster";

            try (final ClusterInfoSession session = factory.createSession();) {
                assertNotNull(pass, session);
                final String clusterPath = recurseCreate(cid, session);
                assertNotNull(pass, clusterPath);

                final String data = "HelloThere";
                session.setData(clusterPath, data);
                final String cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);
            }
        });
    }

    @Test
    public void testSimpleClusterLevelDataThroughApplication() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app3";
            final String dir = "testSimpleClusterLevelDataThroughApplication";

            try (final ClusterInfoSession session = factory.createSession();) {
                assertNotNull(pass, session);
                final String mpapp = recurseCreate(cid, session);
                final String clusterPath = mpapp + "/" + dir;
                assertNotNull(pass, session.mkdir(clusterPath, "YoDude", DirMode.PERSISTENT));
                assertNotNull(pass, clusterPath);
                final Collection<String> clusterPaths = session.getSubdirs(mpapp, null);
                assertNotNull(pass, clusterPaths);
                assertEquals(1, clusterPaths.size());
                assertEquals(dir, clusterPaths.iterator().next());

                final String data = "HelloThere";
                session.setData(clusterPath, data);
                final String cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);
            }
        });
    }

    @Test
    public void testSimpleJoinTest() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app4/test-cluster";

            try (final ClusterInfoSession session = factory.createSession();) {
                assertNotNull(pass, session);
                final String cluster = recurseCreate(cid, session);
                assertNotNull(pass, cluster);

                final String node = cluster + "/Test";
                assertNotNull(session.mkdir(node, null, DirMode.EPHEMERAL));
                assertEquals(1, session.getSubdirs(cluster, null).size());

                final String data = "testSimpleJoinTest-data";
                session.setData(node, data);
                assertEquals(pass, data, session.getData(node, null));

                session.rmdir(node);

                // wait for no more than ten seconds
                assertTrue(pass, poll(10000, cluster, (c) -> session.getSubdirs(c, null).size() == 0));
            }
        });
    }

    private class TestWatcher implements ClusterInfoWatcher {
        public boolean recdUpdate = false;
        public CountDownLatch latch;

        public TestWatcher(final int count) {
            latch = new CountDownLatch(count);
        }

        @Override
        public void process() {
            recdUpdate = true;
            latch.countDown();
        }

    }

    @Test
    public void testSimpleWatcherData() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String app = "/test-app5";
            final String dir = "testSimpleWatcherData";

            final TestWatcher dirWatcher = new TestWatcher(1);
            try (final ClusterInfoSession mainSession = factory.createSession();) {
                assertNotNull(pass, mainSession);

                final String mpapp = recurseCreate(app, mainSession);
                assertTrue(mainSession.exists(mpapp, null));
                mainSession.getSubdirs(mpapp, dirWatcher); // register mainAppWatcher for subdir
                assertEquals(0, mainSession.getSubdirs(mpapp, null).size());

                try (final ClusterInfoSession otherSession = factory.createSession();) {
                    assertNotNull(pass, otherSession);

                    assertFalse(pass, mainSession.equals(otherSession));

                    final String clusterHandle = mpapp + "/" + dir;
                    mainSession.mkdir(clusterHandle, "YoDude", DirMode.PERSISTENT);
                    assertTrue(pass, mainSession.exists(clusterHandle, null));

                    assertTrue(poll(5000, dirWatcher, o -> o.recdUpdate));

                    dirWatcher.recdUpdate = false;

                    final String otherCluster = checkPathExists(clusterHandle, otherSession);
                    assertNotNull(pass, otherCluster);
                    assertEquals(pass, clusterHandle, otherCluster);

                    // in case the mainAppWatcher wrongly receives an update, let's give it a chance.
                    Thread.sleep(500);
                    assertFalse(dirWatcher.recdUpdate);

                    final TestWatcher mainWatcher = new TestWatcher(1);
                    assertTrue(mainSession.exists(clusterHandle, mainWatcher));

                    final TestWatcher otherWatcher = new TestWatcher(1);
                    assertTrue(otherSession.exists(otherCluster, otherWatcher));

                    final String data = "HelloThere";
                    mainSession.setData(clusterHandle, data);

                    // this should have affected otherWatcher
                    assertTrue(pass, otherWatcher.latch.await(5, TimeUnit.SECONDS));
                    assertTrue(pass, otherWatcher.recdUpdate);

                    // we do expect an update here also
                    assertTrue(pass, mainWatcher.latch.await(5, TimeUnit.SECONDS));
                    assertTrue(pass, mainWatcher.recdUpdate);

                    // now check access through both sessions and we should see the update.
                    String cdata = (String) mainSession.getData(clusterHandle, null);
                    assertEquals(pass, data, cdata);

                    cdata = (String) otherSession.getData(otherCluster, null);
                    assertEquals(pass, data, cdata);

                }
            }

            // in case the mainAppWatcher wrongly receives an update, let's give it a chance.
            Thread.sleep(500);
            assertFalse(dirWatcher.recdUpdate);
        });
    }

    @Test
    public void testWatcherOnDataDoesntUpdateOnNewDir() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String app = "/testWatcherOnDataDoesntUpdateOnNewDir";
            final String dir = "mysubdir-testWatcherOnDataDoesntUpdateOnNewDir";

            final TestWatcher dataWatcher = new TestWatcher(0);
            final TestWatcher dirWatcher = new TestWatcher(0);
            try (final ClusterInfoSession mainSession = factory.createSession();) {
                assertNotNull(pass, mainSession);

                final String mpapp = recurseCreate(app, mainSession);
                assertTrue(mainSession.exists(mpapp, dataWatcher));
                assertEquals(0, mainSession.getSubdirs(mpapp, dirWatcher).size());

                try (final ClusterInfoSession otherSession = factory.createSession();) {
                    assertNotNull(pass, otherSession);

                    assertFalse(pass, mainSession.equals(otherSession));

                    final String clusterHandle = mpapp + "/" + dir;
                    otherSession.mkdir(clusterHandle, "YoDude", DirMode.PERSISTENT);
                    assertTrue(pass, otherSession.exists(clusterHandle, null));

                    // First wait for the dir update.
                    assertTrue(pass, poll(5000, dirWatcher, o -> o.recdUpdate));

                    // this is really what we care about. We want to make sure this DOESN"T happen.
                    Thread.sleep(500); // Yes .. I HATE this but I don't know how else to check for an event NOT happening.
                    assertFalse(pass, dataWatcher.recdUpdate); // check we DIDN'T get a message
                }
            }
        });
    }

    @Test
    public void testWatcherOnDirDoesntUpdateOnDataChangeOrNewData() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String app = "/testWatcherOnDirDoesntUpdateOnDataChangeOrNewData";

            final TestWatcher dataWatcher = new TestWatcher(0);
            final TestWatcher dirWatcher = new TestWatcher(0);
            try (final ClusterInfoSession mainSession = factory.createSession();) {
                assertNotNull(pass, mainSession);

                final String mpapp = recurseCreate(app, mainSession);
                assertTrue(mainSession.exists(mpapp, dataWatcher));
                assertEquals(0, mainSession.getSubdirs(mpapp, dirWatcher).size());

                try (final ClusterInfoSession otherSession = factory.createSession();) {
                    assertNotNull(pass, otherSession);

                    assertFalse(pass, mainSession.equals(otherSession));

                    // Update data in that directory

                    otherSession.setData(mpapp, "YoDude");

                    // First wait for the data update.
                    assertTrue(pass, poll(5000, dataWatcher, o -> o.recdUpdate));

                    // this is really what we care about. We want to make sure this DOESN"T happen.
                    Thread.sleep(500); // Yes .. I HATE this but I don't know how else to check for an event NOT happening.
                    assertFalse(pass, dirWatcher.recdUpdate); // check we DIDN'T get a message

                    // now reregister the data watch
                    assertEquals(pass, "YoDude", mainSession.getData(mpapp, dataWatcher));
                    // now remove the data.
                    otherSession.setData(mpapp, null);
                    // watch for the message
                    assertTrue(pass, poll(5000, dataWatcher, o -> o.recdUpdate));

                    // do the non-even check again.
                    Thread.sleep(500); // Yes .. I HATE this but I don't know how else to check for an event NOT happening.
                    assertFalse(pass, dirWatcher.recdUpdate); // check we DIDN'T get a message
                }
            }
        });
    }

    private volatile boolean thread1Passed = false;
    private volatile boolean thread2Passed = false;

    @Test
    public void testConsumerCluster() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);

        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app6/test-cluster";
            try (ClusterInfoSession session1 = factory.createSession();) {
                recurseCreate(cid, session1);
                try (ClusterInfoSession session2 = factory.createSession();) {

                    final Thread t1 = new Thread(() -> {
                        try {
                            final String consumer = checkPathExists(cid, session1);
                            session1.setData(consumer, "Test");
                            thread1Passed = true;
                            latch.countDown();
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    });
                    t1.start();
                    final Thread t2 = new Thread(() -> {
                        try {
                            latch.await(10, TimeUnit.SECONDS);
                            final String producer = checkPathExists(cid, session2);

                            final String data = (String) session2.getData(producer, null);
                            if ("Test".equals(data))
                                thread2Passed = true;
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    });
                    t2.start();

                    t1.join(30000);
                    t2.join(30000); // use a timeout just in case. A failure should be indicated below if the thread never finishes.

                    assertTrue(pass, thread1Passed);
                    assertTrue(pass, thread2Passed);

                }
            }
        });
    }

    @Test
    public void testGetSetDataNoNode() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app7/test-cluster";

            try (final ClusterInfoSession session = factory.createSession();) {
                assertNotNull(pass, session);
                final String cluster = recurseCreate(cid, session);
                assertNotNull(pass, cluster);

                final String node = cluster + "/Test";
                final String data = "testSimpleJoinTest-data";
                boolean gotExpectedException = false;
                try {
                    session.setData(node, data);
                } catch (final ClusterInfoException e) {
                    gotExpectedException = true;
                }
                assertTrue(pass, gotExpectedException);

                gotExpectedException = false;
                try {
                    session.rmdir(node);
                } catch (final ClusterInfoException e) {
                    gotExpectedException = true;
                }
                assertTrue(pass, gotExpectedException);

                gotExpectedException = false;
                try {
                    session.getData(node, null);
                } catch (final ClusterInfoException e) {
                    gotExpectedException = true;
                }
                assertTrue(pass, gotExpectedException);
            }
        });
    }

    @Test
    public void testNullWatcherBehavior() throws Throwable {
        runAllCombinations((pass, factory) -> {
            final String cid = "/test-app2/testNullWatcherBehavior";
            final AtomicBoolean processCalled = new AtomicBoolean(false);
            try (final ClusterInfoSession session = factory.createSession();) {

                assertNotNull(pass, session);
                final String clusterPath = recurseCreate(cid, session);
                assertNotNull(pass, clusterPath);

                final ClusterInfoWatcher watcher = () -> processCalled.set(true);

                assertTrue(session.exists(cid, watcher));

                String data = "HelloThere";
                session.setData(clusterPath, data);

                assertTrue(poll(5000, null, o -> processCalled.get()));

                processCalled.set(false);

                String cdata = (String) session.getData(clusterPath, watcher);
                assertEquals(pass, data, cdata);

                // add the null watcher ...
                cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);

                // but makes sure that it doesn't affect the callback
                Thread.sleep(500); // just in case.
                assertFalse(processCalled.get());

                data += "2";
                session.setData(clusterPath, data);

                assertTrue(poll(5000, null, o -> processCalled.get()));

                cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);
            }
        });
    }

    @Test(expected = ClusterInfoException.class)
    public void testPersistentOnEphemeralDir() throws Throwable {
        runAllCombinations((pass, factory) -> {
            try (final ClusterInfoSession session = factory.createSession();) {
                session.mkdir("/testPersistentOnEphemeralDir", null, DirMode.EPHEMERAL);
                session.mkdir("/testPersistentOnEphemeralDir/subdir", null, DirMode.PERSISTENT);
            }
        });
    }

    @Test(expected = ClusterInfoException.class)
    public void testEphemeralOnEphemeralDir() throws Throwable {
        runAllCombinations((pass, factory) -> {
            try (final ClusterInfoSession session = factory.createSession();) {
                session.mkdir("/testPersistentOnEphemeralDir", null, DirMode.EPHEMERAL);
                session.mkdir("/testPersistentOnEphemeralDir/subdir", null, DirMode.EPHEMERAL);
            }
        });
    }

    @Test
    public void testUnregsiterWatcher() throws Throwable {
        runAllCombinations((pass, factory) -> {
            try (final ClusterInfoSession session = factory.createSession();) {
                session.mkdir("/testUnregsiterWatcher", null, DirMode.PERSISTENT);
                final AtomicLong count = new AtomicLong(0);
                session.getSubdirs("/testUnregsiterWatcher", () -> count.incrementAndGet());

                try (ClusterInfoSession osession = factory.createSession()) {
                    osession.mkdir("/testUnregsiterWatcher/subdir", null, DirMode.EPHEMERAL);

                    assertTrue(pass, poll(5000, count, c -> c.get() == 1));

                    osession.mkdir("/testUnregsiterWatcher/subdir2", null, DirMode.EPHEMERAL);
                    osession.mkdir("/testUnregsiterWatcher/subdir3", null, DirMode.EPHEMERAL);
                    osession.mkdir("/testUnregsiterWatcher/subdir4", null, DirMode.EPHEMERAL);

                    Thread.sleep(300);
                    assertEquals(pass, 1L, count.get());
                }
            }
        });
    }
}
