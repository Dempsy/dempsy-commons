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

package net.dempsy.distconfig.apahcevfs;

import static net.dempsy.distconfig.apahcevfs.Utils.cleanPath;
import static net.dempsy.distconfig.apahcevfs.Utils.getLatest;
import static net.dempsy.distconfig.apahcevfs.Utils.getVersion;
import static net.dempsy.distconfig.apahcevfs.Utils.nextFile;
import static net.dempsy.distconfig.apahcevfs.Utils.wrap;

import java.io.IOException;

import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.PropertiesWatcher;

public class ApacheVfsPropertiesReader implements PropertiesReader {
    private static Logger LOGGER = LoggerFactory.getLogger(ApacheVfsPropertiesReader.class);
    private final FileObject parentDirObj;

    public ApacheVfsPropertiesReader(final String parentUri, final String childPropertiesName) throws IOException {
        final FileObject baseDir = wrap(() -> VFS.getManager().resolveFile(parentUri));
        parentDirObj = wrap(() -> VFS.getManager().resolveFile(baseDir, cleanPath(childPropertiesName)));
    }

    public ApacheVfsPropertiesReader(final String pathUri) throws IOException {
        parentDirObj = wrap(() -> VFS.getManager().resolveFile(pathUri));
    }

    private static class Proxy implements FileListener {
        final FileObject beingWatched;
        final PropertiesWatcher watcher;

        Proxy(final FileObject beingWatched, final PropertiesWatcher watcher) {
            this.beingWatched = beingWatched;
            this.watcher = watcher;
        }

        @Override
        public void fileCreated(final FileChangeEvent event) throws Exception {
            doIt();
        }

        @Override
        public void fileDeleted(final FileChangeEvent event) throws Exception {
            doIt();
        }

        @Override
        public void fileChanged(final FileChangeEvent event) throws Exception {
            doIt();
        }

        void doIt() {
            beingWatched.getFileSystem().removeListener(beingWatched, this);
            try {
                watcher.propertiesChanged();
            } catch (final RuntimeException rte) {
                LOGGER.error("Failed processing notification that a properties file changed.", rte);
            }
        }
    }

    @Override
    public VersionedProperties read(final PropertiesWatcher watcher) throws IOException {
        final FileObject latest = getLatest(parentDirObj);
        final int ver = latest == null ? -1 : getVersion(latest);
        final VersionedProperties ret = new VersionedProperties(ver, Utils.read(latest));
        if (watcher != null) {
            // if there's a watcher ....
            final FileObject next = nextFile(latest, parentDirObj);
            final Proxy proxy = new Proxy(next, watcher);
            next.getFileSystem().addListener(next, proxy);

            // but now, if the file exists due to a race condition, lets at least address that.
            if (next.exists())
                proxy.doIt();
        }
        return ret;
    }

    @Override
    public boolean supportsNotification() {
        return true;
    }
}
