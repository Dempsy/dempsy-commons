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

import org.junit.After;

import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.local.LocalClusterSessionFactory;
import net.dempsy.distconfig.AutoCloseableFunction;
import net.dempsy.distconfig.PropertiesStore;
import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.TestConfigImplementation;

public class TestLocalClusterDistConfig extends TestConfigImplementation {

    private final LocalClusterSessionFactory factory = new LocalClusterSessionFactory();

    @After
    public void after() {
        LocalClusterSessionFactory.reset();
    }

    @Override
    protected AutoCloseableFunction<PropertiesStore> getLoader(final String testName) throws Exception {
        return new AutoCloseableFunction<PropertiesStore>() {
            ClusterInfoSession session = factory.createSession();

            @Override
            public PropertiesStore apply(final String path) {
                return new ClusterInfoPropertiesLoader(session, path);
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
