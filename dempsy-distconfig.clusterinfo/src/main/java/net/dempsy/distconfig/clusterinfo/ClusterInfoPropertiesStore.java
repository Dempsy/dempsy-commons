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

import static net.dempsy.distconfig.clusterinfo.Utils.mkdir;
import static net.dempsy.distconfig.clusterinfo.Utils.versionSubdirPrefix;
import static net.dempsy.distconfig.clusterinfo.Utils.versionSubdirPrefixLen;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.DirMode;
import net.dempsy.distconfig.PropertiesStore;

public class ClusterInfoPropertiesStore extends PropertiesStore {

    private final ClusterInfoSession session;
    private final String path;

    public ClusterInfoPropertiesStore(final ClusterInfoSession session, final String path) {
        this.session = session;

        this.path = Utils.cleanPath(path);
    }

    @Override
    public int push(final Properties props) {
        return write(props);
    }

    @Override
    public int merge(final Properties props) {
        final Properties setProps = new ClusterInfoPropertiesReader(session, path).read(null);

        final Properties newProps = new Properties();
        newProps.putAll(setProps);
        newProps.putAll(props);

        return write(newProps);
    }

    @Override
    public int clear(final String... propNames) throws IOException {
        final Properties setProps = new ClusterInfoPropertiesReader(session, path).read(null);

        final Properties newProps = new Properties();
        newProps.putAll(setProps);
        Arrays.stream(propNames).forEach(newProps::remove);

        return write(newProps);
    }

    private int write(final Properties prop) {
        try {
            // make sure the path exists
            mkdir(session, path);
            final String ret = session.mkdir(path + versionSubdirPrefix, prop, DirMode.PERSISTENT_SEQUENTIAL);
            session.setData(path, prop);
            return Integer.parseInt(ret.substring(ret.lastIndexOf(versionSubdirPrefix) + versionSubdirPrefixLen, ret.length()));
        } catch (final ClusterInfoException cie) {
            throw new RuntimeException(cie); // not great.
        }
    }

}
