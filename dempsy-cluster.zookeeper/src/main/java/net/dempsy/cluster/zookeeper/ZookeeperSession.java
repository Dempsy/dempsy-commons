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

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.ClusterInfoWatcher;
import net.dempsy.cluster.DirMode;
import net.dempsy.cluster.DisruptibleSession;
import net.dempsy.serialization.Serializer;
import net.dempsy.util.SafeString;
import net.dempsy.util.executor.AutoDisposeSingleThreadScheduler;

public class ZookeeperSession implements ClusterInfoSession, DisruptibleSession {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperSession.class);

    private static final byte[] zeroByteArray = new byte[0];

    // =======================================================================
    // Manage the mapping between DirMode and ZooKeeper's CreateMode.
    // =======================================================================
    private static CreateMode[] dirModeLut = new CreateMode[4];

    static {
        dirModeLut[DirMode.PERSISTENT.flag] = CreateMode.PERSISTENT;
        dirModeLut[DirMode.EPHEMERAL.flag] = CreateMode.EPHEMERAL;
        dirModeLut[DirMode.PERSISTENT_SEQUENTIAL.flag] = CreateMode.PERSISTENT_SEQUENTIAL;
        dirModeLut[DirMode.EPHEMERAL_SEQUENTIAL.flag] = CreateMode.EPHEMERAL_SEQUENTIAL;
    }

    private static CreateMode from(final DirMode mode) {
        return dirModeLut[mode.flag];
    }

    // =======================================================================

    // Accessed from test.
    volatile AtomicReference<ZooKeeper> zkref;

    private volatile boolean isRunning = true;
    protected long resetDelay = 500;
    protected String connectString;
    protected int sessionTimeout;
    private final Serializer serializer;

    private final Set<WatcherProxy> registeredWatchers = new HashSet<WatcherProxy>();

    protected ZookeeperSession(final Serializer serializer, final String connectString, final int sessionTimeout) throws IOException {
        this.connectString = connectString;
        this.sessionTimeout = sessionTimeout;
        this.serializer = serializer;
        this.zkref = new AtomicReference<ZooKeeper>();
        final ZooKeeper newZk = makeZooKeeperClient(connectString, sessionTimeout);
        if (newZk != null)
            setNewZookeeper(newZk);
    }

    private static class DirModeObject {
        public final DirMode dirMode;
        public final Object value;

        public DirModeObject(final DirMode dirMode, final Object value) {
            this.dirMode = dirMode;
            this.value = value;
        }
    }

    @Override
    public String mkdir(final String path, final Object data, final DirMode mode) throws ClusterInfoException {
        return (String) callZookeeper("mkdir", path, null, new DirModeObject(mode, data), (cur, pa, watcher, ud) -> {
            final DirModeObject userdata = (DirModeObject) ud;
            final Object info = userdata.value;

            return cur.create(path, (info == null ? zeroByteArray : serializer.serialize(info)), Ids.OPEN_ACL_UNSAFE, from(userdata.dirMode));
        }, true);

    }

    @Override
    public void rmdir(final String ppath) throws ClusterInfoException {
        callZookeeper("rmdir", ppath, null, null, (cur, pa, wp, userdata) -> {
            cur.delete(pa, -1);
            return null;
        }, false);
    }

    @Override
    public boolean exists(final String ppath, final ClusterInfoWatcher watcher) throws ClusterInfoException {
        final Object ret = callZookeeper("exists", ppath, watcher, null, (cur, path, wp, userdata) -> {
            return wp == null ? (cur.exists(path, true) == null ? false : true) : (cur.exists(path, wp) == null ? false : true);
        }, false);
        return ((Boolean) ret).booleanValue();
    }

    @Override
    public Object getData(final String ppath, final ClusterInfoWatcher watcher) throws ClusterInfoException {
        return callZookeeper("getData", ppath, watcher, null, (cur, path, wp, userdata) -> {
            final byte[] ret = wp == null ? cur.getData(path, true, null) : cur.getData(path, wp, null);

            if (ret != null && ret.length > 0)
                return serializer.deserialize(ret, Object.class);
            return null;
        }, false);
    }

    @Override
    public void setData(final String ppath, final Object info) throws ClusterInfoException {
        callZookeeper("mkdir", ppath, null, info, (cur, path, wp, userdata) -> {
            byte[] buf = null;
            if (info != null)
                // Serialize to a byte array
                buf = serializer.serialize(info);

            zkref.get().setData(path, buf, -1);
            return null;
        }, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<String> getSubdirs(final String ppath, final ClusterInfoWatcher pwatcher) throws ClusterInfoException {
        return (Collection<String>) callZookeeper("getSubdirs", ppath, pwatcher, null,
                (cur, pa, wp, userdata) -> wp == null ? cur.getChildren(pa, true) : cur.getChildren(pa, wp), false);
    }

    @Override
    public void stop() {
        AtomicReference<ZooKeeper> curZk;
        synchronized (this) {
            isRunning = false;
            curZk = zkref;
            zkref = null; // this blows up any more usage
        }

        try {
            curZk.get().close();
        } catch (final Throwable th) { /* let it go otherwise */}
    }

    @Override
    public void disrupt() {
        if (logger.isTraceEnabled())
            logger.trace("Disrupting Zookeeper session by closing the session.");
        if (zkref != null) {
            try {
                final ZooKeeper cur = zkref.get();
                cur.close();
            } catch (final Throwable th) {
                logger.error("Failed disrupting ZookeeperSession", th);
            }
        }
    }

    protected static class ZkWatcher implements Watcher {
        @Override
        public void process(final WatchedEvent event) {
            if (logger.isTraceEnabled())
                logger.trace("CALLBACK:Main Watcher:" + event);
        }
    }

    /**
     * This is defined here to be overridden in a test.
     */
    protected ZooKeeper makeZooKeeperClient(final String connectString, final int sessionTimeout) throws IOException {
        if (logger.isTraceEnabled())
            logger.trace("creating new ZooKeeper client connection from scratch.");

        return new ZooKeeper(connectString, sessionTimeout, new ZkWatcher());
    }

    // protected for test access only
    protected class WatcherProxy implements Watcher {
        private final ClusterInfoWatcher watcher;

        public WatcherProxy(final ClusterInfoWatcher watcher) {
            this.watcher = watcher;
        }

        @Override
        public void process(final WatchedEvent event) {
            if (logger.isTraceEnabled())
                logger.trace("Process called on " + SafeString.objectDescription(watcher) + " with ZooKeeper event " + event);

            // if we're stopped then just quit
            if (zkref == null)
                return;

            // if we're disconnected then we need to reset and skip an
            // current processing.
            if (event != null && event.getState() == Event.KeeperState.Disconnected)
                resetZookeeper(zkref.get());
            else {
                synchronized (registeredWatchers) {
                    registeredWatchers.remove(this);
                }

                try {
                    synchronized (this) {
                        watcher.process();
                    }
                } catch (final RuntimeException rte) {
                    logger.warn("Watcher " + SafeString.objectDescription(watcher) +
                            " threw an exception in it's \"process\" call.", rte);
                }
            }
        }

        @Override
        public boolean equals(final Object o) {
            return watcher.equals(((WatcherProxy) o).watcher);
        }

        @Override
        public int hashCode() {
            return watcher.hashCode();
        }

        @Override
        public String toString() {
            return SafeString.valueOfClass(watcher);
        }
    }

    @FunctionalInterface
    private interface ZookeeperCall {
        public Object call(ZooKeeper cur, String path, WatcherProxy watcher, Object userdata)
                throws KeeperException, InterruptedException, IOException;
    }

    // This is broken out in order to be intercepted in tests
    protected WatcherProxy makeWatcherProxy(final ClusterInfoWatcher watcher) {
        return new WatcherProxy(watcher);
    }

    private Object callZookeeper(final String name, final String path, final ClusterInfoWatcher watcher, final Object userdata,
            final ZookeeperCall callee, final boolean mkdir) throws ClusterInfoException {
        if (isRunning) {
            final WatcherProxy wp = watcher != null ? makeWatcherProxy(watcher) : null;
            if (wp != null) {
                synchronized (registeredWatchers) {
                    registeredWatchers.add(wp);
                }
            }

            final ZooKeeper cur = zkref.get();
            try {
                return callee.call(cur, path, wp, userdata);
            } catch (final KeeperException.NodeExistsException e) {
                if (logger.isTraceEnabled())
                    logger.trace("Failed call to " + name + " at " + path + " because the node already exists:" + e.getLocalizedMessage());
                return null; // this is only thrown from mkdir and so if the Node Exists
                             // we simply want to return a null String
            } catch (final KeeperException.NoNodeException e) {
                if (mkdir)
                    throw new ClusterInfoException.NoParentException("No parent node for " + path + " while running " + name, e);
                else
                    throw new ClusterInfoException.NoNodeException("Node doesn't exist at " + path + " while running " + name, e);
            } catch (final KeeperException e) {
                resetZookeeper(cur);
                throw new ClusterInfoException("Zookeeper failed while trying to " + name + " at " + path, e);
            } catch (final InterruptedException e) {
                throw new ClusterInfoException("Interrupted while trying to " + name + " at " + path, e);
            } catch (final IOException e) {
                throw new ClusterInfoException("Failed to deserialize the object durring a " + name + " call at " + path, e);
            }
        }

        throw new ClusterInfoException(name + " called on stopped ZookeeperSession.");
    }

    private final AutoDisposeSingleThreadScheduler scheduler = new AutoDisposeSingleThreadScheduler("Zookeeper Session Reset");
    private volatile AutoDisposeSingleThreadScheduler.Cancelable beingReset = null;

    private synchronized void resetZookeeper(final ZooKeeper failedZooKeeper) {
        if (!isRunning)
            logger.error("resetZookeeper called on stopped ZookeeperSession.");

        try {
            final AtomicReference<ZooKeeper> tmpZkRef = zkref;
            // if we're not shutting down (which would be indicated by tmpZkRef == null
            // and if the failedInstance we're trying to reset is the current one, indicated by tmpZkRef.get() == failedInstance
            // and if we're not already working on beingReset
            if (tmpZkRef != null && tmpZkRef.get() == failedZooKeeper && (beingReset == null || beingReset.isDone())) {
                final Runnable runnable = new Runnable() {
                    ZooKeeper failedInstance = failedZooKeeper;

                    @Override
                    public void run() {
                        if (logger.isTraceEnabled())
                            logger.trace("Executing ZooKeeper client reset.");

                        ZooKeeper newZk = null;
                        try {
                            boolean forceRebuild = false;
                            if (failedInstance.getState().isAlive()) {
                                // try to use it
                                try {
                                    failedInstance.exists("/", null);
                                    if (logger.isTraceEnabled())
                                        logger.trace("client reset determined the failedInstance is now working.");
                                } catch (final KeeperException th) {
                                    if (logger.isTraceEnabled())
                                        logger.trace("client reset determined the failedInstance is not yet working.");

                                    // if the data directory on the server has gone away and the server was restarted we get into a
                                    // situation where we get continuous ConnectionLossExceptions and we can't tell the difference
                                    // between this state and when the connection is simply not reachable.
                                    if (th instanceof KeeperException.ConnectionLossException && haveBeenAbleToReachAServer())
                                        forceRebuild = true;
                                    else
                                        // just reschedule and exit.
                                        return;
                                }
                            }

                            // we should only recreate the client if it's closed.
                            newZk = failedInstance.getState().isAlive() && !forceRebuild ? failedInstance
                                    : makeZooKeeperClient(connectString, sessionTimeout);

                            // this is true if the reset worked and we're not in the process
                            // of shutting down.
                            if (newZk != null) {
                                // we want the setNewZookeeper and the clearing of the
                                // beingReset flag to be atomic so future failures that result
                                // in calls to resetZookeeper will either:
                                // 1) be skipped because they are for an older ZooKeeper instance.
                                // 2) be executed because they are for this new ZooKeeper instance.
                                // what we don't want is the possibility that the reset will be skipped
                                // even though the reset is called for this new ZooKeeper, but we haven't cleared
                                // the beingReset flag yet.
                                synchronized (ZookeeperSession.this) {
                                    setNewZookeeper(newZk);
                                    beingReset = null;
                                }

                                // now notify the watchers
                                final Collection<WatcherProxy> twatchers = new ArrayList<WatcherProxy>();
                                synchronized (registeredWatchers) {
                                    twatchers.addAll(registeredWatchers);
                                }

                                for (final WatcherProxy watcher : twatchers)
                                    watcher.process(null);
                            }
                        } catch (final Throwable e) {
                            logger.warn("Failed to reset the ZooKeeper connection to " + connectString, e);
                            newZk = null;
                        } finally {
                            if (newZk == null && isRunning)
                                // reschedule me.
                                beingReset = scheduler.schedule(this, resetDelay, TimeUnit.MILLISECONDS);
                        }

                    }

                    private long startTime = -1;

                    private boolean haveBeenAbleToReachAServer() {
                        if (logger.isTraceEnabled())
                            logger.trace("testing to see if something is listening on " + connectString);

                        // try to create a tcp connection to any of the servers.
                        final String[] hostPorts = connectString.split(",");
                        for (final String hostPort : hostPorts) {
                            final String[] hostAndPort = hostPort.split(":");

                            Socket socket = null;
                            try {
                                socket = new Socket(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
                                if (startTime == -1)
                                    startTime = System.currentTimeMillis();
                                return System.currentTimeMillis() - startTime > (1.5 * sessionTimeout);
                            } catch (final IOException ioe) {} finally {
                                if (socket != null) {
                                    try {
                                        socket.close();
                                    } catch (final Throwable th) {}
                                }
                            }
                        }

                        startTime = -1;
                        return false;
                    }

                };

                beingReset = scheduler.schedule(runnable, 1, TimeUnit.NANOSECONDS);
            }
        } catch (final Throwable re) {
            beingReset = null;
            logger.error("resetZookeeper failed for attempted reset to " + connectString, re);
        }

    }

    private synchronized void setNewZookeeper(final ZooKeeper newZk) {
        if (logger.isTraceEnabled())
            logger.trace("reestablished connection to " + connectString);

        if (isRunning) {
            final ZooKeeper last = zkref.getAndSet(newZk);
            if (last != null && last != newZk) {
                try {
                    last.close();
                } catch (final Throwable th) {}
            }
        } else {
            // in this case with zk == null we're shutting down.
            try {
                newZk.close();
            } catch (final Throwable th) {}
        }
    }

}
