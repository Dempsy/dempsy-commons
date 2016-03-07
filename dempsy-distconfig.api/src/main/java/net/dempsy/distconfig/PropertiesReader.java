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

package net.dempsy.distconfig;

import java.io.IOException;
import java.util.Properties;

/**
 * Implementations of this interface can be used to retrieve a set of properties while optionally registering for updates when properties change.
 */
public interface PropertiesReader {

    /**
     * Stored {@link Properties} along with the version number.
     */
    public static class VersionedProperties extends Properties {
        private static final long serialVersionUID = 1L;

        /**
         * Version of the corresponding properties.
         */
        public final int version;

        public VersionedProperties(final int version, final Properties props) {
            this.version = version;
            if (props != null)
                this.putAll(props);
        }
    }

    /**
     * Retrieves the latest {@link Properties} file that's been registered.
     * 
     * @param watcher
     *            if non-null the watcher will be called back when the properties change from the version provided. Upon callback the {@link PropertiesWatcher} will be unregistered. A subsequent read will need
     *            to be called in order to re-register for another change.
     * @return the latest {@link VersionedProperties}
     * @throws IOException
     *             if the underlying transport or storage mechanism throws an IOException
     * @throws UnsupportedOperationException
     *             if the underlying implementation doesn't support notification but watcher is not null.
     */
    public VersionedProperties read(PropertiesWatcher watcher) throws IOException, UnsupportedOperationException;

    /**
     * @return Whether or not the underlying implementation supports notification.
     */
    public boolean supportsNotification();
}
