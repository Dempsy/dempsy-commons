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

package net.dempsy.distconfig.clusterinfo;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;

import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.zookeeper.ZookeeperSessionFactory;
import net.dempsy.cluster.zookeeper.ZookeeperTestServer;
import net.dempsy.distconfig.AutoCloseableFunction;
import net.dempsy.distconfig.PropertiesStore;
import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.TestConfigImplementation;
import net.dempsy.serialization.jackson.JsonSerializer;

public class TestZookeeperClusterDistConfig extends TestConfigImplementation {

    private ZookeeperTestServer server = null;
    private ZookeeperSessionFactory factory = null;

    @Before
    public void before() throws IOException {
        server = new ZookeeperTestServer();
        factory = new ZookeeperSessionFactory(server.connectString(), 5000, new JsonSerializer());
    }

    @After
    public void after() {
        server.close();
        server = null;
        factory = null;
    }

    @Override
    protected AutoCloseableFunction<PropertiesStore> getLoader(final String testName) throws Exception {
        return new AutoCloseableFunction<PropertiesStore>() {
            ClusterInfoSession session = factory.createSession();

            @Override
            public PropertiesStore apply(final String path) {
                return new ClusterInfoPropertiesStore(session, path);
            }

            @Override
            public void close() throws Exception {
                session.close();
            }
        };
    }

    @Override
    protected AutoCloseableFunction<PropertiesReader> getReader(final String testName) throws Exception {
        return new AutoCloseableFunction<PropertiesReader>() {
            ClusterInfoSession session = factory.createSession();

            @Override
            public PropertiesReader apply(final String path) {
                return new ClusterInfoPropertiesReader(session, path);
            }

            @Override
            public void close() throws Exception {
                session.close();
            }
        };
    }

}
