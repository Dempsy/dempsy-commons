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

import net.dempsy.cluster.ClusterInfoException;
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.DirMode;

class Utils {

    static final String versionSubdirPrefix = "/v_";
    static final int versionSubdirPrefixLen = versionSubdirPrefix.length();

    static String cleanPath(final String path) {
        String trimmed = path.trim();
        if (!trimmed.startsWith("/"))
            trimmed = "/" + trimmed;
        if (trimmed.endsWith("/"))
            trimmed = trimmed.substring(0, trimmed.length() - 1); // chop off trailing '/'. Let's hope there's not more than one.
        return trimmed;
    }

    static void mkdir(final ClusterInfoSession session, final String path) throws ClusterInfoException {
        final String[] elements = path.substring(1).split("/");
        String cur = "";
        for (final String element : elements) {
            cur = cur + "/" + element;
            session.mkdir(cur, null, DirMode.PERSISTENT);
        }
    }
}
