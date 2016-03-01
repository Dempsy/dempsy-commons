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

package net.dempsy.cluster.zookeeper;

import static net.dempsy.cluster.TestClusterImpls.recurseCreate;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoWatcher;
import net.dempsy.cluster.DirMode;
import net.dempsy.serialization.jackson.JsonSerializer;
import net.dempsy.utils.test.ConditionPoll.Condition;

/**
 * The goal here is to make sure the cluster is always consistent even if it looses the zookeeper session connection or doesn't have it to begin with.
 */
public class TestZookeeperClusterResilience {
    public static final String appname = TestZookeeperClusterResilience.class.getSimpleName();
    private static Logger logger = LoggerFactory.getLogger(TestZookeeperClusterResilience.class);
    static private final long baseTimeoutMillis = 20000;

    public static abstract class TestWatcher implements ClusterInfoWatcher {
        AtomicBoolean called = new AtomicBoolean(false);
        ZookeeperSession session;

        public TestWatcher(final ZookeeperSession session) {
            this.session = session;
        }

    }

    volatile boolean connected = false;

    @Test
    public void testBouncingServer() throws Throwable {

        final String clusterId = "/" + appname + "/testBouncingServer";

        try (final ZookeeperTestServer server = new ZookeeperTestServer()) {
            final ZookeeperSessionFactory factory = new ZookeeperSessionFactory(server.connectString(), 5000, new JsonSerializer());
            try (ZookeeperSession session = (ZookeeperSession) factory.createSession();) {
                final ZookeeperSession cluster = session;
                recurseCreate(clusterId, session);
                final TestWatcher callback = new TestWatcher(cluster) {

                    @Override
                    public void process() {
                        boolean done = false;
                        while (!done) {
                            done = true;

                            try {
                                if (session.getSubdirs(clusterId, this).size() == 0)
                                    session.mkdir(clusterId + "/slot1", null, DirMode.EPHEMERAL);
                                called.set(true);
                            } catch (final ClusterInfoException.NoNodeException e) {
                                try {
                                    recurseCreate(clusterId, session);
                                    done = false;
                                } catch (final ClusterInfoException e1) {
                                    throw new RuntimeException(e1);
                                }
                            } catch (final ClusterInfoException e) {
                                // this will fail when the connection is severed... that's ok.
                            }
                        }
                    }

                };

                cluster.exists(clusterId, callback);
                callback.process();

                // create another session and look
                try (final ZookeeperSession tmpsession = (ZookeeperSession) factory.createSession();) {
                    assertEquals(1, tmpsession.getSubdirs("/" + appname + "/testBouncingServer", null).size());
                }

                // kill the server.
                server.shutdown(false);

                // reset the flags
                callback.called.set(false);

                // restart the server
                server.start(false);

                // wait for the call
                assertTrue(poll(baseTimeoutMillis, callback, new Condition<TestWatcher>() {
                    @Override
                    public boolean conditionMet(final TestWatcher o) {
                        return o.called.get();
                    }
                }));

                // get the view from a new session.
                try (final ZookeeperSession tmpsession = (ZookeeperSession) factory.createSession();) {
                    assertEquals(1, tmpsession.getSubdirs("/" + appname + "/testBouncingServer", null).size());
                }
            }
        }
    }

    @Test
    public void testBouncingServerWithCleanDataDir() throws Throwable {
        final String clusterId = "/" + appname + "/testBouncingServerWithCleanDataDir";

        try (final ZookeeperTestServer server = new ZookeeperTestServer()) {
            final ZookeeperSessionFactory factory = new ZookeeperSessionFactory(server.connectString(), 5000, new JsonSerializer());
            try (final ZookeeperSession session = (ZookeeperSession) factory.createSession();) {
                final ZookeeperSession cluster = session;
                recurseCreate(clusterId, session);
                final TestWatcher callback = new TestWatcher(cluster) {

                    @Override
                    public void process() {
                        boolean done = false;
                        while (!done) {
                            done = true;

                            try {
                                if (session.getSubdirs(clusterId, this).size() == 0)
                                    session.mkdir(clusterId + "/slot1", null, DirMode.EPHEMERAL);
                                called.set(true);
                            } catch (final ClusterInfoException.NoNodeException e) {
                                try {
                                    recurseCreate(clusterId, session);
                                    done = false;
                                } catch (final ClusterInfoException e1) {
                                    throw new RuntimeException(e1);
                                }
                            } catch (final ClusterInfoException e) {
                                // this will fail when the connection is severed... that's ok.
                            }
                        }
                    }

                };

                cluster.exists(clusterId, callback);
                callback.process();

                // create another session and look
                try (final ZookeeperSession session2 = (ZookeeperSession) factory.createSession();) {
                    assertEquals(1, session2.getSubdirs("/" + appname + "/testBouncingServerWithCleanDataDir", null).size());
                }

                // kill the server.
                server.shutdown(true);

                // reset the flags
                callback.called.set(false);

                // restart the server
                server.start(true);

                // wait for the call
                assertTrue(poll(baseTimeoutMillis, callback, new Condition<TestWatcher>() {
                    @Override
                    public boolean conditionMet(final TestWatcher o) {
                        return o.called.get();
                    }
                }));

                // get the view from a new session.
                try (final ZookeeperSession session2 = (ZookeeperSession) factory.createSession();) {
                    assertEquals(1, session2.getSubdirs("/" + appname + "/testBouncingServerWithCleanDataDir", null).size());
                }
            }
        }
    }

    @Test
    public void testNoServerOnStartup() throws Throwable {
        final Properties zkConfig = ZookeeperTestServer.genZookeeperConfig();
        final String connectString = ZookeeperTestServer.connectString(zkConfig);
        final int portx = ZookeeperTestServer.getPort(zkConfig);

        // create a session factory
        final ZookeeperSessionFactory factory = new ZookeeperSessionFactory(connectString, 5000, new JsonSerializer());

        // create a session from the session factory
        try (final ZookeeperSession session = (ZookeeperSession) factory.createSession();) {

            final String clusterId = "/" + appname + "/testNoServerOnStartup";

            // hook a test watch to make sure that callbacks work correctly
            final TestWatcher callback = new TestWatcher(session) {
                @Override
                public void process() {
                    called.set(true);
                }
            };

            // now accessing the cluster should get us an error.
            boolean gotCorrectError = false;
            try {
                session.getSubdirs(clusterId, callback);
            } catch (final ClusterInfoException e) {
                gotCorrectError = true;
            }
            assertTrue(gotCorrectError);

            // now lets startup the server.
            try (final ZookeeperTestServer server = new ZookeeperTestServer(zkConfig);) {

                // create a cluster from the session
                recurseCreate(clusterId, session);

                // wait until this works.
                assertTrue(poll(baseTimeoutMillis, callback, new Condition<TestWatcher>() {
                    @Override
                    public boolean conditionMet(final TestWatcher o) {
                        return o.called.get();
                    }
                }));

                callback.called.set(false); // reset the callbacker ...

                // now see if the cluster works.
                assertTrue(poll(baseTimeoutMillis, callback, new Condition<TestWatcher>() {
                    @Override
                    public boolean conditionMet(final TestWatcher o) {
                        return !o.called.get();
                    }
                }));

                session.getSubdirs(clusterId, callback);

                final ZooKeeper origZk = session.zkref.get();
                ZookeeperTestServer.forceSessionExpiration(origZk, portx);

                // wait for the callback
                assertTrue(poll(baseTimeoutMillis, callback, new Condition<TestWatcher>() {
                    @Override
                    public boolean conditionMet(final TestWatcher o) {
                        return o.called.get();
                    }
                }));

                // unfortunately I cannot check the getActiveSlots for failure because there's a race condition I can't fix.
                // No matter how fast I check it's possible that it's okay again OR that allSlots hasn't been cleared.
                //
                // however, they should eventually recover.
                gotCorrectError = true;
                for (final long endTime = System.currentTimeMillis() + baseTimeoutMillis; endTime > System.currentTimeMillis() && gotCorrectError;) {
                    Thread.sleep(1);
                    try {
                        session.getSubdirs(clusterId, callback);
                        gotCorrectError = false;
                    } catch (final ClusterInfoException e) {}
                }

                session.getSubdirs(clusterId, callback);

                // And join should work
                gotCorrectError = true;
                for (final long endTime = System.currentTimeMillis() + baseTimeoutMillis; endTime > System.currentTimeMillis() && gotCorrectError;) {
                    Thread.sleep(1);
                    try {
                        session.mkdir(clusterId + "/join-1", null, DirMode.EPHEMERAL);
                        gotCorrectError = false;
                    } catch (final ClusterInfoException e) {}
                }

                assertFalse(gotCorrectError);
            }
        }
    }

    @Test
    public void testSessionExpired() throws Throwable {
        // now lets startup the server.
        try (ZookeeperTestServer server = new ZookeeperTestServer();
                ZookeeperSession session = new ZookeeperSession(new JsonSerializer(), server.connectString(), 5000);) {

            // the createExpireSessionClient actually results in a Disconnected/SyncConnected rotating events.
            // ... so we need to filter those out since it will result in a callback.
            final String clusterId = "/" + appname + "/testSessionExpired";
            recurseCreate(clusterId, session);
            final TestWatcher callback = new TestWatcher(session) {
                @Override
                public void process() {
                    try {
                        called.set(true);
                        logger.trace("process called on TestWatcher.");
                        session.exists(clusterId, this);
                        session.getSubdirs(clusterId, this);
                    } catch (final ClusterInfoException cie) {
                        throw new RuntimeException(cie);
                    }
                }

            };

            // now see if the cluster works.
            callback.process(); // this registers the session with the callback as the Watcher

            // now reset the condition
            callback.called.set(false);

            ZookeeperTestServer.forceSessionExpiration(session.zkref.get(), server.port);

            // we should see the session expiration in a callback
            assertTrue(poll(5000, callback, new Condition<TestWatcher>() {
                @Override
                public boolean conditionMet(final TestWatcher o) {
                    return o.called.get();
                }
            }));

            // and eventually a reconnect
            assertTrue(poll(5000, callback, new Condition<TestWatcher>() {
                @Override
                public boolean conditionMet(final TestWatcher o) {
                    try {
                        o.process();
                        return true;
                    } catch (final Throwable th) {
                        return false;
                    }
                }
            }));

            recurseCreate(clusterId, session);
            assertTrue(session.exists(clusterId, callback));
        }
    }

    private final AtomicBoolean forceIOException = new AtomicBoolean(false);
    private final CountDownLatch forceIOExceptionLatch = new CountDownLatch(5);

    @Test
    public void testRecoverWithIOException() throws Throwable {
        // now lets startup the server.
        try (ZookeeperTestServer server = new ZookeeperTestServer();
                final ZookeeperSession session = new ZookeeperSession(new JsonSerializer(), server.connectString(), 5000) {
                    @Override
                    protected ZooKeeper makeZooKeeperClient(final String connectString, final int sessionTimeout) throws IOException {
                        if (forceIOException.get()) {
                            forceIOExceptionLatch.countDown();
                            throw new IOException("Fake IO Problem.");
                        }
                        return super.makeZooKeeperClient(connectString, sessionTimeout);
                    }
                };) {
            final String clusterId = "/" + appname + "/testRecoverWithIOException";
            recurseCreate(clusterId, session);
            final TestWatcher callback = new TestWatcher(session) {
                @Override
                public void process() {
                    try {
                        session.getSubdirs(clusterId, this);
                        called.set(true);
                    } catch (final ClusterInfoException cie) {
                        throw new RuntimeException(cie);
                    }
                }
            };

            callback.process();

            // force the ioexception to happen
            forceIOException.set(true);

            ZookeeperTestServer.forceSessionExpiration(session.zkref.get(), server.port);

            // now in the background it should be retrying but hosed.
            assertTrue(forceIOExceptionLatch.await(baseTimeoutMillis * 3, TimeUnit.MILLISECONDS));

            // now the getActiveSlots call should fail since i'm preventing the recovery by throwing IOExceptions
            assertTrue(poll(baseTimeoutMillis, clusterId, new Condition<String>() {
                @Override
                public boolean conditionMet(final String o) throws Throwable {
                    try {
                        session.mkdir(o + "/join-1", null, DirMode.EPHEMERAL);
                        return false;
                    } catch (final ClusterInfoException e) {
                        return true;
                    }
                }
            }));

            callback.called.set(false); // reset the callbacker ...

            // now we should allow the code to proceed.
            forceIOException.set(false);

            // wait for the callback
            assertTrue(poll(baseTimeoutMillis, callback, new Condition<TestWatcher>() {
                @Override
                public boolean conditionMet(final TestWatcher o) {
                    return o.called.get();
                }
            }));

            // this should eventually recover.
            assertTrue(poll(baseTimeoutMillis, clusterId, new Condition<String>() {
                @Override
                public boolean conditionMet(final String o) throws Throwable {
                    try {
                        recurseCreate(o, session);
                        session.mkdir(o + "/join-1", null, DirMode.EPHEMERAL);
                        return true;
                    } catch (final ClusterInfoException e) {
                        return false;
                    }
                }
            }));

            session.getSubdirs(clusterId, callback);

            // And join should work
            // And join should work
            assertTrue(poll(baseTimeoutMillis, clusterId, new Condition<String>() {
                @Override
                public boolean conditionMet(final String o) throws Throwable {
                    try {
                        session.mkdir(o + "/join-1", null, DirMode.EPHEMERAL);
                        return true;
                    } catch (final ClusterInfoException e) {}
                    return false;
                }
            }));
        }
    }
}
