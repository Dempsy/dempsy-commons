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

package net.dempsy.cluster.local;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.ClusterInfoSessionFactory;
import net.dempsy.cluster.ClusterInfoWatcher;
import net.dempsy.cluster.DirMode;
import net.dempsy.cluster.DisruptibleSession;

/**
 * This class is for running all cluster management from within the same vm, and for the same vm. It's meant to mimic the Zookeeper implementation such that callbacks are not made to watchers registered to
 * sessions through which changes are made.
 */
public class LocalClusterSessionFactory implements ClusterInfoSessionFactory {
    private static Logger LOGGER = LoggerFactory.getLogger(LocalClusterSessionFactory.class);
    protected static List<LocalSession> currentSessions = new ArrayList<LocalSession>();
    protected final boolean cleanupAfterLastSession;

    public LocalClusterSessionFactory(final boolean cleanupAfterLastSession) {
        this.cleanupAfterLastSession = cleanupAfterLastSession;
    }

    public LocalClusterSessionFactory() {
        this(true);
    }

    // ====================================================================
    // This section pertains to the management of the tree information
    private static Map<String, Entry> entries = new HashMap<String, Entry>();

    static {
        reset();
    }

    /// initially add the root.
    public static synchronized void reset() {
        entries.clear();
        entries.put("/", new Entry(null, null));
    }

    public static synchronized void completeReset() {
        synchronized (currentSessions) {
            if (!isReset())
                LOGGER.error("LocalClusterSessionFactory beging reset with sessions or entries still open.");

            final List<LocalSession> sessions = new ArrayList<LocalSession>(currentSessions.size());
            sessions.addAll(currentSessions);
            currentSessions.clear();
            for (final LocalSession session : sessions)
                session.stop(false);
            reset();
        }
    }

    public static boolean isReset() {
        return currentSessions.size() == 0 && entries.size() == 1;
    }

    private static synchronized Set<LocalSession.WatcherProxy> ogatherWatchers(final Entry ths, final boolean node, final boolean child) {
        final Set<LocalSession.WatcherProxy> twatchers = new HashSet<LocalSession.WatcherProxy>();
        if (node) {
            twatchers.addAll(ths.nodeWatchers);
            ths.nodeWatchers = new HashSet<LocalSession.WatcherProxy>();
        }
        if (child) {
            twatchers.addAll(ths.childWatchers);
            ths.childWatchers = new HashSet<LocalSession.WatcherProxy>();
        }
        return twatchers;
    }

    private static class Entry {
        private final AtomicReference<Object> data = new AtomicReference<Object>();
        private Set<LocalSession.WatcherProxy> nodeWatchers = new HashSet<LocalSession.WatcherProxy>();
        private Set<LocalSession.WatcherProxy> childWatchers = new HashSet<LocalSession.WatcherProxy>();
        private final Collection<String> children = new ArrayList<String>();
        private final Map<String, AtomicLong> childSequences = new HashMap<String, AtomicLong>();

        private volatile boolean inProcess = false;
        private final Lock processLock = new ReentrantLock();
        private final DirMode mode;

        public Entry(final Object data, final DirMode mode) {
            this.data.set(data);
            this.mode = mode;
        }

        @Override
        public String toString() {
            return children.toString() + " " + valueOf(data.get());
        }

        private Set<LocalSession.WatcherProxy> gatherWatchers(final boolean node, final boolean child) {
            return ogatherWatchers(this, node, child);
        }

        private final Set<LocalSession.WatcherProxy> toCallQueue = new HashSet<LocalSession.WatcherProxy>();

        private void callWatchers(final boolean node, final boolean child) {
            Set<LocalSession.WatcherProxy> twatchers = gatherWatchers(node, child);

            processLock.lock();
            try {
                if (inProcess) {
                    toCallQueue.addAll(twatchers);
                    return;
                }

                do {
                    inProcess = true;

                    // remove everything in twatchers from the toCallQueue
                    // since we are about to call them all. If some end up back
                    // on here then when we're done the toCallQueue will not be empty
                    // and we'll run it again.
                    toCallQueue.removeAll(twatchers);

                    for (final LocalSession.WatcherProxy watcher : twatchers) {
                        try {
                            processLock.unlock();
                            watcher.process();
                        } catch (final RuntimeException e) {
                            LOGGER.error("Failed to handle process for watcher " + objectDescription(watcher), e);
                        } finally {
                            processLock.lock();
                        }
                    }

                    // now we need to reset twatchers to any new toCallQueue
                    twatchers = new HashSet<LocalSession.WatcherProxy>();
                    twatchers.addAll(toCallQueue); // in case we run again

                } while (toCallQueue.size() > 0);

                inProcess = false;
            } finally {
                processLock.unlock();
            }
        }
    }

    private static class EntryAndPath {
        public final Entry entry;
        public final String pathToUse;

        public EntryAndPath(final Entry entry, final String pathToUse) {
            this.entry = entry;
            this.pathToUse = pathToUse;
        }
    }

    private static String parent(final String path) {
        final File f = new File(path);
        return f.getParent().replace('\\', '/');
    }

    // This should only be called from a static synchronized method on the LocalClusterSessionFactory
    private static Entry get(final String absolutePath, final LocalSession.WatcherProxy watcher, final boolean nodeWatch)
            throws ClusterInfoException.NoNodeException {
        Entry ret;
        ret = entries.get(absolutePath);
        if (ret == null)
            throw new ClusterInfoException.NoNodeException("Path \"" + absolutePath + "\" doesn't exists.");
        if (watcher != null) {
            if (nodeWatch) {
                ret.nodeWatchers.add(watcher);
                if (LOGGER.isTraceEnabled())
                    LOGGER.trace("Added [" + watcher.watcher + "] to " + ret + " at " + absolutePath);
            } else {
                ret.childWatchers.add(watcher);
                if (LOGGER.isTraceEnabled())
                    LOGGER.trace("Added [" + watcher.watcher + "] to " + ret + " at " + absolutePath);
            }
        }
        return ret;
    }

    private static synchronized Object ogetData(final String path, final LocalSession.WatcherProxy watcher) throws ClusterInfoException {
        final Entry e = get(path, watcher, true);
        return e.data.get();
    }

    private static void osetData(final String path, final Object data) throws ClusterInfoException {
        final Entry e;
        synchronized (LocalClusterSessionFactory.class) {
            e = get(path, null, true);
            e.data.set(data);
        }
        e.callWatchers(true, false);
    }

    private static synchronized boolean oexists(final String path, final LocalSession.WatcherProxy watcher) {
        final Entry e = entries.get(path);
        if (e != null && watcher != null)
            e.nodeWatchers.add(watcher);
        return e != null;
    }

    private static String omkdir(final String path, final Object data, final DirMode mode) throws ClusterInfoException {
        final EntryAndPath results = doomkdir(path, data, mode);
        final Entry parent = results.entry;
        final String pathToUse = results.pathToUse;

        if (parent != null)
            parent.callWatchers(false, true);
        return pathToUse;
    }

    private static synchronized EntryAndPath doomkdir(final String path, final Object data, final DirMode mode) throws ClusterInfoException {
        if (oexists(path, null))
            return new EntryAndPath(null, null);

        final String parentPath = parent(path);

        final Entry parent = entries.get(parentPath);
        if (parent == null)
            throw new ClusterInfoException.NoParentException("No Parent for \"" + path + "\" which is expected to be \"" +
                    parent(path) + "\"");

        if (parent.mode != null && parent.mode.isEphemeral())
            throw new ClusterInfoException(
                    "Cannot add the subdirectory \"" + path + "\" to the EPHEMERAL parent directory \"" + parentPath
                            + ".\" EPHEMERAL directories can't have children.");

        long seq = -1;
        if (mode.isSequential()) {
            AtomicLong cseq = parent.childSequences.get(path);
            if (cseq == null)
                parent.childSequences.put(path, cseq = new AtomicLong(0));
            seq = cseq.getAndIncrement();
        }

        final String pathToUse = seq >= 0 ? (path + String.format("%010d", seq)) : path;

        entries.put(pathToUse, new Entry(data, mode));
        // find the relative path
        final int lastSlash = pathToUse.lastIndexOf('/');
        parent.children.add(pathToUse.substring(lastSlash + 1));
        return new EntryAndPath(parent, pathToUse);
    }

    private static void ormdir(final String path) throws ClusterInfoException {
        ormdir(path, true);
    }

    private static void ormdir(final String path, final boolean notifyWatchers) throws ClusterInfoException {
        final EntryAndParent results = doormdir(path);
        final Entry ths = results.entry;
        final Entry parent = results.parent;

        if (parent != null && notifyWatchers)
            parent.callWatchers(false, true);

        if (notifyWatchers)
            ths.callWatchers(true, true);
    }

    private static class EntryAndParent {
        public final Entry entry;
        public final Entry parent;

        public EntryAndParent(final Entry entry, final Entry parent) {
            this.entry = entry;
            this.parent = parent;
        }
    }

    private static synchronized EntryAndParent doormdir(final String path) throws ClusterInfoException {
        final Entry ths = entries.get(path);
        if (ths == null)
            throw new ClusterInfoException("rmdir of non existant node \"" + path + "\"");

        final Entry parent = entries.get(parent(path));
        entries.remove(path);

        if (parent != null) {
            final int lastSlash = path.lastIndexOf('/');
            parent.children.remove(path.substring(lastSlash + 1));
        }

        return new EntryAndParent(ths, parent);
    }

    private static synchronized Collection<String> ogetSubdirs(final String path, final LocalSession.WatcherProxy watcher)
            throws ClusterInfoException {
        final Entry e = get(path, watcher, false);
        final Collection<String> ret = new ArrayList<String>(e.children.size());
        ret.addAll(e.children);
        return ret;
    }
    // ====================================================================

    @Override
    public ClusterInfoSession createSession() {
        synchronized (currentSessions) {
            final LocalSession ret = new LocalSession();
            currentSessions.add(ret);
            return ret;
        }
    }

    public class LocalSession implements ClusterInfoSession, DisruptibleSession {
        private final List<String> localEphemeralDirs = new ArrayList<String>();
        private final AtomicBoolean stopping = new AtomicBoolean(false);

        private class WatcherProxy {
            private final ClusterInfoWatcher watcher;

            private WatcherProxy(final ClusterInfoWatcher watcher) {
                this.watcher = watcher;
            }

            private final void process() {
                if (!stopping.get())
                    watcher.process();
            }

            @Override
            public int hashCode() {
                return watcher.hashCode();
            }

            @Override
            public boolean equals(final Object o) {
                return watcher.equals(((WatcherProxy) o).watcher);
            }

            @Override
            public String toString() {
                return watcher.toString();
            }
        }

        private final WatcherProxy makeWatcher(final ClusterInfoWatcher watcher) {
            return watcher == null ? null : new WatcherProxy(watcher);
        }

        @Override
        public String mkdir(final String path, final Object data, final DirMode mode) throws ClusterInfoException {
            if (stopping.get())
                throw new ClusterInfoException("mkdir called on stopped session.");

            final String ret = omkdir(path, data, mode);
            if (ret != null && mode.isEphemeral()) {
                synchronized (localEphemeralDirs) {
                    localEphemeralDirs.add(ret);
                }
            }
            return ret;
        }

        @Override
        public void rmdir(final String path) throws ClusterInfoException {
            if (stopping.get())
                throw new ClusterInfoException("rmdir called on stopped session.");

            ormdir(path);
            synchronized (localEphemeralDirs) {
                localEphemeralDirs.remove(path);
            }
        }

        @Override
        public boolean exists(final String path, final ClusterInfoWatcher watcher) throws ClusterInfoException {
            if (stopping.get())
                throw new ClusterInfoException("exists called on stopped session.");
            return oexists(path, makeWatcher(watcher));
        }

        @Override
        public Object getData(final String path, final ClusterInfoWatcher watcher) throws ClusterInfoException {
            if (stopping.get())
                throw new ClusterInfoException("getData called on stopped session.");
            return ogetData(path, makeWatcher(watcher));
        }

        @Override
        public void setData(final String path, final Object data) throws ClusterInfoException {
            if (stopping.get())
                throw new ClusterInfoException("setData called on stopped session.");
            osetData(path, data);
        }

        @Override
        public Collection<String> getSubdirs(final String path, final ClusterInfoWatcher watcher) throws ClusterInfoException {
            if (stopping.get())
                throw new ClusterInfoException("getSubdirs called on stopped session.");
            return ogetSubdirs(path, makeWatcher(watcher));
        }

        @Override
        public void stop() {
            stop(true);
        }

        private void stop(final boolean notifyWatchers) {
            stopping.set(true);
            synchronized (localEphemeralDirs) {
                for (int i = localEphemeralDirs.size() - 1; i >= 0; i--) {
                    try {
                        if (LOGGER.isTraceEnabled())
                            LOGGER.trace("Removing ephemeral directory due to stopped session " + localEphemeralDirs.get(i));
                        ormdir(localEphemeralDirs.get(i), notifyWatchers);
                    } catch (final ClusterInfoException cie) {
                        // this can only happen in an odd race condition but
                        // it's ok if it does since it means the dir has already
                        // been removed from another thread.
                    }
                }
                localEphemeralDirs.clear();
            }

            synchronized (currentSessions) {
                currentSessions.remove(this);
                if (currentSessions.size() == 0 && cleanupAfterLastSession)
                    reset();
            }
        }

        @Override
        public void disrupt() {
            // first dump the ephemeral nodes
            final Set<String> parents = new HashSet<String>();
            synchronized (localEphemeralDirs) {
                for (int i = localEphemeralDirs.size() - 1; i >= 0; i--) {
                    try {
                        ormdir(localEphemeralDirs.get(i), false);
                    } catch (final ClusterInfoException cie) {
                        // this can only happen in an odd race condition but
                        // it's ok if it does since it means the dir has already
                        // been removed from another thread.
                    }
                }

                // go through all of the nodes that were just deleted and find all unique parents
                for (final String path : localEphemeralDirs)
                    parents.add(parent(path));

                localEphemeralDirs.clear();
            }
            // In some tests (and this method is only for tests) there is a race condition where
            // the test has a thread trying to grab a shard while disrupting the session in order
            // to knock out anyone currently holding the shard. On heavily loaded machines this
            // doesn't work because the callback is notified too quickly never giving the test
            // enough opportunity to obtain the shard. So here we are going to sleep for some
            // short amount of time before making the callback.
            try {
                Thread.sleep(200);
            } catch (final InterruptedException ie) {}
            for (final String path : parents) {
                try {
                    final Entry e = get(path, null, false);
                    e.callWatchers(false, true);
                } catch (final ClusterInfoException.NoNodeException e) {} // this is fine
            }
        }
    } // end session definition

    private static String valueOf(final Object o) {
        try {
            return String.valueOf(o);
        } catch (final Throwable th) {
            LOGGER.warn("Failed to determine valueOf for given object", th);
        }

        return "[error]";
    }

    private static String valueOfClass(final Object o) {
        try {
            final Class<?> clazz = o == null ? null : o.getClass();
            return clazz == null ? "[null object has no class]" : clazz.getName();
        } catch (final Throwable th) {
            LOGGER.warn("Failed to determine valueOf for given object", th);
        }

        return "[error]";
    }

    private static String objectDescription(final Object message) {
        return "\"" + valueOf(message) +
                (message != null ? "\" of type \"" + valueOfClass(message) : "") +
                "\"";
    }

}
