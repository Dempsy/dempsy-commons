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

import java.util.Collection;

/**
 * <p>
 * This is the main interface for the dempsy-cluster.api abstraction. If you're familiar with ZooKeeper then this should be completely straightforward.
 * </p>
 * <p>
 * Cluster information is stored in a file system like tree structure where information is addressable using a '/' separated path. Each node in the tree can optionally contain a single data {@link Object}
 * (NOTE: the main implementation (dempsy-cluster.zookeeper) uses dempsy-serialization.api so the data may need to be serializable according to whatever serialization scheme is registered with that
 * Implementation).
 * </p>
 * <p>
 * Implementations of {@link ClusterInfoWatcher}s can be registered with any node in the tree to receive notifications of changes at that node. To register for changes to the data at a node, you can use
 * {@link #exists(String, ClusterInfoWatcher)} or {@link #getData(String, ClusterInfoWatcher)}. For example:
 * </p>
 * 
 * <pre>
 * {@code
 *  final ClusterInfoSession session = factory.createSession();
 *  session.mkdir("/root",null,DirMode.EPHEMERAL); // make parent dir
 *  session.mkdir("/root/subdir", null, DirMode.EPHEMERAL); // make child dir
 *  session.exists("/root/subdir", () -> System.out.prinln("The data at /root/subdir has changed")); // <- lambda callback will be invoked when the data changes.
 *  
 *  ....
 *  otherSession.setData("/root/subdir", new MyDataObject()); // <- this will cause the lambda to execute.
 * }
 * </pre>
 * 
 * <p>
 * Note that there is a distinction made between registering for changes that include new or deleted subdirectories, and changes that include the data at a location changing. To register for changes to
 * subdirectories of a particular node in the tree, use {@link #getSubdirs(String, ClusterInfoWatcher)}. For example:
 * </p>
 * 
 * <pre>
 * {@code
 *  final ClusterInfoSession session = factory.createSession();
 *  session.mkdir("/root",null,DirMode.EPHEMERAL); // make parent dir
 *  session.getSubdirs("/root",() -&gt; System.out.prinln("The subdirs of /root have changed")); // - lambda callback will be invoked when subdirs come or go. *     });
 *  
 *  ....
 *  otherSession.mkdir("/root/subdir", null, DirMode.EPHEMERAL); // - this will cause the lambda to execute.
 * }
 * </pre>
 * 
 * <p>
 * SEQUENTIAL directories will appear as separately versioned directories when retrieved using {@link #getSubdirs(String, ClusterInfoWatcher)}. They will appear as different subdirectories with a suffix. For
 * example:
 * </p>
 * 
 * <pre>
 * {@code
 * ...
 *  session.mkdir("/root/subdir_", null, DirMode.EPHEMERAL_SEQUENTIAL); // make child dir
 *  session.mkdir("/root/subdir_", null, DirMode.EPHEMERAL_SEQUENTIAL); // make child dir
 *  session.mkdir("/root/subdir_", null, DirMode.EPHEMERAL_SEQUENTIAL); // make child dir
 *  Collection<String> subdirs = session.getSubdirs("/root");
 *  subdirs.stream().forEach(s -> System.out.println(s));
 *  ....
 * }
 * </pre>
 * <p>
 * This will result in something like the following:
 * </p>
 * 
 * <pre>
 * subdir_0000000000
 * subdir_0000000001
 * subdir_0000000002
 * </pre>
 * 
 * <p>
 * The version suffixes are both lexographically sortable and also convertable to integers. This should be guaranteed by every implementation.
 * </p>
 * 
 */
public interface ClusterInfoSession extends AutoCloseable {

    public default void recursiveMkdir(final String path, final DirMode mode) throws ClusterInfoException {
        final String[] splitPath = path.split("/");
        String parent = "";
        for (final String p : splitPath) {
            final String curSubdir = p.trim();
            if (curSubdir.length() > 0) { // avoid leading or trailing slashes
                final String cur = parent + "/" + curSubdir;
                mkdir(cur, null, mode);
                parent = cur;
            }
        }

    }

    /**
     * This will create a node at the given path. A node must be created before it can be used. This method
     * is not recursive so parent directories will need to be created before this one is created.
     * 
     * @param path
     *            a '/' separated path to a directory in the cluster information manager to create.
     * @param data
     *            optional data to set for that directory
     * @param mode
     *            is the mode to set for the new directory. See {@link DirMode}.
     * 
     * @return directory path if the directory was created. {@code null} if the directory cannot be created
     * or already exists. If the {@link DirMode} is sequential then the result will satisfy the SEQUENTIAL
     * requirements (see {@link DirMode} for the details).
     * 
     * @throws ClusterInfoException
     *             on an error which can include the fact that the parent directory doesn't exist or if you 
     *             add a directory as a subdir of an EPHEMERAL directory.
     */
    public String mkdir(String path, Object data, DirMode mode) throws ClusterInfoException;

    /**
     * This will remove the directory stored at the path. The directory can be deleted if there is a data object stored in it but it cannot be deleted it it has children. They should be removed first.
     * 
     * @param path
     *            is the directory to delete.
     * @throws ClusterInfoException
     *             if there is no node at the given path.
     */
    public void rmdir(String path) throws ClusterInfoException;

    /**
     * Check if a path has already been created. If the path exists, and if watcher is non-null then the watcher will be called back when the node at the path has data added removed or changed. Once the
     * notification takes place the watch is unregistered.
     * 
     * @param path
     *            is the directory to check
     * 
     * @param watcher
     *            if non-null, and the path exists, then the watcher will be called back whenever the node at the path has data added to it, or is deleted.
     * 
     * @return true if the node exists, false otherwise.
     * 
     * @throws ClusterInfoException
     *             if there is an unforeseen problem.
     */
    public boolean exists(String path, ClusterInfoWatcher watcher) throws ClusterInfoException;

    /**
     * If data is stored at the node indicated by the path, then the data will be returned. If the node exists, and if watcher is non-null then the watcher will be called back when the node at the path has data
     * added removed or changed. Once the notification takes place the watch is unregistered.
     * 
     * @param path
     *            place to put the data.
     * @param watcher
     *            if non-null, and the path exists, then the watcher will be called back whenever the node at the path has data added to it, or is deleted.
     * @return the Object stored at the location.
     * @throws ClusterInfoException
     */
    public Object getData(String path, ClusterInfoWatcher watcher) throws ClusterInfoException;

    /**
     * Set the data associated with the tree node identified by the path. Any data watches set will be triggered.
     * 
     * @param path
     *            identify the directory/tree node of the place to store the data
     * @param data
     *            the data to put at the given location.
     * @throws ClusterInfoException
     */
    public void setData(String path, Object data) throws ClusterInfoException;

    /**
     * Show all of the subdirectories of the given directory. If the path exists, and if watcher is non-null then the watcher will be called back when any subdirectories are added removed or changed. Once the
     * notification takes place the watch is unregistered.
     * 
     * @param path
     *            where the subdirectories to show are.
     * @param watcher
     *            if non-null, and the path exists, then the watcher will be called back whenever the node at the path has data added to it, or is deleted.
     * @return the list of subdirectories. These strings WILL NOT contain the full path. They will only contain the subdirectory name. If they are versions of the same SEQUENTIAL directory then they will be
     *         lexographically sortable.
     * @throws ClusterInfoException
     */
    public Collection<String> getSubdirs(String path, ClusterInfoWatcher watcher) throws ClusterInfoException;

    /**
     * Stop the session. Stopping the session should cause all EPHEMERAL nodes that were created by the session to be removed.
     * 
     * stop() must be implemented such that it doesn't throw an exception no matter what but forces the stopping of any underlying resources that require stopping. Stop is expected to manage the stopping of all
     * underlying MpClusters that it created and once complete no more MpWatcher callbacks should be, or be able to, execute.
     * 
     * NOTE: stop() must be idempotent.
     */
    public void stop();

    /**
     * default implementation of the {@link AutoCloseable} interface. By default it simply calls {@link #stop()}.
     */
    @Override
    default public void close() {
        stop();
    }

}
