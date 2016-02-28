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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.ClusterInfoSessionFactory;
import net.dempsy.cluster.ClusterInfoWatcher;
import net.dempsy.cluster.DirMode;
import net.dempsy.utils.test.ConditionPoll.Condition;

public abstract class TestClusterImpls {
    private final ClusterInfoSessionFactory[] clusterFactories;
    private final List<ClusterInfoSession> sessionsToClose = new ArrayList<ClusterInfoSession>();

    protected TestClusterImpls(final ClusterInfoSessionFactory... clusterFactories) {
        this.clusterFactories = clusterFactories;
    }

    private static interface Checker {
        public void check(String pass, ClusterInfoSessionFactory factory) throws Throwable;
    }

    private <T, N> void runAllCombinations(final Checker checker) throws Throwable {
        for (final ClusterInfoSessionFactory factory : clusterFactories) {

            if (checker != null)
                checker.check("pass for:" + factory.getClass().getSimpleName(), factory);

        }
    }

    private static String getClusterLeaf(final ClusterId cid, final ClusterInfoSession session) throws ClusterInfoException {
        return session.exists(cid.asPath(), null) ? cid.asPath() : null;
    }

    public static String createApplicationLevel(final ClusterId cid, final ClusterInfoSession session) throws ClusterInfoException {
        final String ret = "/" + cid.namespace;
        session.mkdir(ret, null, DirMode.PERSISTENT);
        return ret;
    }

    public static String createClusterLevel(final ClusterId cid, final ClusterInfoSession session) throws ClusterInfoException {
        String ret = createApplicationLevel(cid, session);
        ret += ("/" + cid.clusterName);
        session.mkdir(ret, null, DirMode.PERSISTENT);
        return ret;
    }

    @Test
    public void testMpClusterFromFactory() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app1", "test-cluster");

                final ClusterInfoSession session = factory.createSession();
                assertNotNull(pass, session);
                sessionsToClose.add(session);
                final String clusterPath = createClusterLevel(cid, session);

                assertEquals(clusterPath, cid.asPath());

                assertNotNull(pass, clusterPath);
                assertTrue(pass, session.exists(clusterPath, null));

                // there should be nothing currently registered
                final Collection<String> slots = session.getSubdirs(clusterPath, null);
                assertNotNull(pass, slots);
                assertEquals(pass, 0, slots.size());

                assertNull(pass, session.getData(clusterPath, null));

                session.stop();
            }

        });
    }

    @Test
    public void testSimpleClusterLevelData() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app2", "test-cluster");

                final ClusterInfoSession session = factory.createSession();
                assertNotNull(pass, session);
                sessionsToClose.add(session);
                final String clusterPath = createClusterLevel(cid, session);
                assertNotNull(pass, clusterPath);

                final String data = "HelloThere";
                session.setData(clusterPath, data);
                final String cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);

                session.stop();
            }

        });
    }

    @Test
    public void testSimpleClusterLevelDataThroughApplication() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app3", "testSimpleClusterLevelDataThroughApplication");

                final ClusterInfoSession session = factory.createSession();
                assertNotNull(pass, session);
                sessionsToClose.add(session);
                final String mpapp = createApplicationLevel(cid, session);
                final String clusterPath = mpapp + "/" + cid.clusterName;
                assertNotNull(pass, session.mkdir(clusterPath, "YoDude", DirMode.PERSISTENT));
                assertNotNull(pass, clusterPath);
                final Collection<String> clusterPaths = session.getSubdirs(mpapp, null);
                assertNotNull(pass, clusterPaths);
                assertEquals(1, clusterPaths.size());
                assertEquals(cid.clusterName, clusterPaths.iterator().next());

                final String data = "HelloThere";
                session.setData(clusterPath, data);
                final String cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);

                session.stop();

            }

        });
    }

    @Test
    public void testSimpleJoinTest() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app4", "test-cluster");

                final ClusterInfoSession session = factory.createSession();
                assertNotNull(pass, session);
                sessionsToClose.add(session);
                final String cluster = createClusterLevel(cid, session);
                assertNotNull(pass, cluster);

                final String node = cluster + "/Test";
                assertNotNull(session.mkdir(node, null, DirMode.EPHEMERAL));
                assertEquals(1, session.getSubdirs(cluster, null).size());

                final String data = "testSimpleJoinTest-data";
                session.setData(node, data);
                assertEquals(pass, data, session.getData(node, null));

                session.rmdir(node);

                // wait for no more than ten seconds
                assertTrue(pass, poll(10000, cluster, new Condition<String>() {
                    @Override
                    public boolean conditionMet(final String cluster) throws Throwable {
                        return session.getSubdirs(cluster, null).size() == 0;
                    }
                }));

                session.stop();
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
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app5", "test-cluster");

                final ClusterInfoSession mainSession = factory.createSession();
                assertNotNull(pass, mainSession);
                sessionsToClose.add(mainSession);

                final TestWatcher mainAppWatcher = new TestWatcher(1);
                final String mpapp = createApplicationLevel(cid, mainSession);
                assertTrue(mainSession.exists(mpapp, null));
                mainSession.getSubdirs(mpapp, mainAppWatcher); // register mainAppWatcher for subdir
                assertEquals(0, mainSession.getSubdirs(mpapp, null).size());

                final ClusterInfoSession otherSession = factory.createSession();
                assertNotNull(pass, otherSession);
                sessionsToClose.add(otherSession);

                assertFalse(pass, mainSession.equals(otherSession));

                final String clusterHandle = mpapp + "/" + cid.clusterName;
                mainSession.mkdir(clusterHandle, "YoDude", DirMode.PERSISTENT);
                assertTrue(pass, mainSession.exists(clusterHandle, null));

                assertTrue(poll(5000, mainAppWatcher, new Condition<TestWatcher>() {
                    @Override
                    public boolean conditionMet(final TestWatcher o) {
                        return o.recdUpdate;
                    }
                }));

                mainAppWatcher.recdUpdate = false;

                final String otherCluster = getClusterLeaf(cid, otherSession);
                assertNotNull(pass, otherCluster);
                assertEquals(pass, clusterHandle, otherCluster);

                // in case the mainAppWatcher wrongly receives an update, let's give it a chance.
                Thread.sleep(500);
                assertFalse(mainAppWatcher.recdUpdate);

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

                mainSession.stop();
                otherSession.stop();

                // in case the mainAppWatcher wrongly receives an update, let's give it a chance.
                Thread.sleep(500);
                assertFalse(mainAppWatcher.recdUpdate);
            }

        });
    }

    private ClusterInfoSession session1;
    private ClusterInfoSession session2;
    private volatile boolean thread1Passed = false;
    private volatile boolean thread2Passed = false;
    private final CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testConsumerCluster() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app6", "test-cluster");
                session1 = factory.createSession();
                createClusterLevel(cid, session1);
                sessionsToClose.add(session1);
                session2 = factory.createSession();
                sessionsToClose.add(session2);

                final Thread t1 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String consumer = getClusterLeaf(cid, session1);
                            session1.setData(consumer, "Test");
                            thread1Passed = true;
                            latch.countDown();
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }

                    }
                });
                t1.start();
                final Thread t2 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            latch.await(10, TimeUnit.SECONDS);
                            final String producer = getClusterLeaf(cid, session2);

                            final String data = (String) session2.getData(producer, null);
                            if ("Test".equals(data))
                                thread2Passed = true;
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                t2.start();

                t1.join(30000);
                t2.join(30000); // use a timeout just in case. A failure should be indicated below if the thread never finishes.

                assertTrue(pass, thread1Passed);
                assertTrue(pass, thread2Passed);

                session2.stop();
                session1.stop();
            }
        });
    }

    @Test
    public void testGetSetDataNoNode() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app7", "test-cluster");

                final ClusterInfoSession session = factory.createSession();
                assertNotNull(pass, session);
                sessionsToClose.add(session);
                final String cluster = createClusterLevel(cid, session);
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

                session.stop();
            }
        });
    }

    @Test
    public void testNullWatcherBehavior() throws Throwable {
        runAllCombinations(new Checker() {
            @Override
            public void check(final String pass, final ClusterInfoSessionFactory factory) throws Throwable {
                final ClusterId cid = new ClusterId("test-app2", "testNullWatcherBehavior");
                final AtomicBoolean processCalled = new AtomicBoolean(false);
                final ClusterInfoSession session = factory.createSession();

                assertNotNull(pass, session);
                sessionsToClose.add(session);
                final String clusterPath = createClusterLevel(cid, session);
                assertNotNull(pass, clusterPath);

                final ClusterInfoWatcher watcher = new ClusterInfoWatcher() {
                    @Override
                    public void process() {
                        processCalled.set(true);
                    }
                };

                assertTrue(session.exists(cid.asPath(), watcher));

                String data = "HelloThere";
                session.setData(clusterPath, data);

                assertTrue(poll(5000, null, new Condition<Object>() {
                    @Override
                    public boolean conditionMet(final Object o) {
                        return processCalled.get();
                    }
                }));

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

                assertTrue(poll(5000, null, new Condition<Object>() {
                    @Override
                    public boolean conditionMet(final Object o) {
                        return processCalled.get();
                    }
                }));

                cdata = (String) session.getData(clusterPath, null);
                assertEquals(pass, data, cdata);
                session.stop();
            }

        });
    }

}
