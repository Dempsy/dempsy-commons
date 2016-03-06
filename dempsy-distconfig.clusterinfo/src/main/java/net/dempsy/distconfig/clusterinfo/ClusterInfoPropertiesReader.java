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

import static net.dempsy.distconfig.clusterinfo.Utils.cleanPath;
import static net.dempsy.distconfig.clusterinfo.Utils.mkdir;
import static net.dempsy.distconfig.clusterinfo.Utils.versionSubdirPrefixLen;

import java.util.Collection;
import java.util.Properties;
import java.util.function.BinaryOperator;

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.PropertiesWatcher;

public class ClusterInfoPropertiesReader implements PropertiesReader {
    private final ClusterInfoSession session;
    private final String path;

    public ClusterInfoPropertiesReader(final ClusterInfoSession session, final String path) {
        this.session = session;
        this.path = Utils.cleanPath(path);
    }

    private final BinaryOperator<String> findLatest = (final String prev, final String cur) -> prev == null ? cur
            : (prev.compareTo(cur) > 0 ? prev : cur);

    @Override
    public VersionedProperties read(final PropertiesWatcher watcher) {
        try {
            if (!session.exists(path, null)) {
                if (watcher == null)
                    return new VersionedProperties(-1, null);
                else {
                    // we need to register a watcher so we need to create the dir.
                    mkdir(session, cleanPath(path));
                    return read(watcher); // recurse ... curse again
                }
            }

            final Collection<String> subdirs = session.getSubdirs(path, watcher == null ? null : (() -> watcher.propertiesChanged()));
            // the latest version will be the highest number
            final String curDir = subdirs.stream().reduce(null, findLatest, findLatest);
            if (curDir == null)
                return new VersionedProperties(-1, null);
            final int latestVersion = Integer.parseInt(curDir.substring(versionSubdirPrefixLen, curDir.length()));
            return new VersionedProperties(latestVersion,
                    (Properties) session.getData(path + "/" + curDir, watcher == null ? null : (() -> watcher.propertiesChanged())));
        } catch (final ClusterInfoException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean supportsNotification() {
        return true;
    }
}
