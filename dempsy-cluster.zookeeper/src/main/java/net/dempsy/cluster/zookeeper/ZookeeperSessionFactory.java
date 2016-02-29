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

import org.apache.zookeeper.ZooKeeper;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.ClusterInfoSessionFactory;
import net.dempsy.serialization.Serializer;
import net.dempsy.util.SafeString;

public class ZookeeperSessionFactory implements ClusterInfoSessionFactory {
    private final String connectString;
    private final int sessionTimeout;
    private final Serializer ser;

    public ZookeeperSessionFactory(final String connectString, final int sessionTimeout, final Serializer ser) {
        this.connectString = connectString;
        this.sessionTimeout = sessionTimeout;
        this.ser = ser;
    }

    @Override
    public ClusterInfoSession createSession() throws ClusterInfoException {
        ZookeeperSession ret;

        // create a new zookeeper instance
        try {
            ret = new ZookeeperSession(ser, connectString, sessionTimeout);
        } catch (final IOException ioe) {
            throw new ClusterInfoException("Failed to instantiate a ZooKeeper client (" +
                    ZooKeeper.class.getSimpleName() + ") using the connectString:\"" +
                    SafeString.valueOf(connectString) + "\" with the sessionTimeout:" +
                    sessionTimeout, ioe);
        }

        return ret;
    }
}
