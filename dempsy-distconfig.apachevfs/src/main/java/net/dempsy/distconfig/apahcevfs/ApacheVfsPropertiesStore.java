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
import static net.dempsy.distconfig.apahcevfs.Utils.read;
import static net.dempsy.util.Functional.mapChecked;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Function;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;

import net.dempsy.distconfig.PropertiesStore;

public class ApacheVfsPropertiesStore extends PropertiesStore {

    private final FileObject parentDirObj;
    private final static String COMMENT = "These properties loaded using " + ApacheVfsPropertiesStore.class.getSimpleName();

    private final static Function<Exception, IOException> em = e -> IOException.class.isAssignableFrom(e.getClass()) ? (IOException) e
            : new IOException(e);

    public ApacheVfsPropertiesStore(final String parentUri, final String childPropertiesName) throws IOException {
        final FileObject baseDir = mapChecked(() -> VFS.getManager().resolveFile(parentUri), em);
        parentDirObj = mapChecked(() -> VFS.getManager().resolveFile(baseDir, cleanPath(childPropertiesName)), em);
    }

    public ApacheVfsPropertiesStore(final String pathUri) throws IOException {
        parentDirObj = mapChecked(() -> VFS.getManager().resolveFile(pathUri), em);
    }

    @Override
    public int push(final Properties props) throws IOException {
        return mapChecked(() -> {
            final FileObject next = nextFile(getLatest(parentDirObj), parentDirObj);
            try (OutputStream os = next.getContent().getOutputStream()) {
                props.store(os, COMMENT);
            }
            return new Integer(getVersion(next));
        } , em).intValue();
    }

    @Override
    public int merge(final Properties props) throws IOException {
        return mapChecked(() -> {
            final FileObject latest = getLatest(parentDirObj);
            if (latest == null)
                return push(props);

            final Properties oldProps = read(latest);
            final Properties newProps = new Properties();
            newProps.putAll(oldProps);
            newProps.putAll(props);

            final FileObject next = nextFile(latest, parentDirObj);
            try (OutputStream os = next.getContent().getOutputStream()) {
                newProps.store(os, COMMENT);
            }
            return new Integer(getVersion(next));
        } , em).intValue();
    }

    @Override
    public int clear(final String... props) throws IOException {
        return mapChecked(() -> {
            final FileObject latest = getLatest(parentDirObj);
            if (latest == null)
                return -1;

            final Properties oldProps = read(latest);
            final Properties newProps = new Properties();
            newProps.putAll(oldProps);
            Arrays.stream(props).forEach(newProps::remove);

            final FileObject next = nextFile(latest, parentDirObj);
            try (OutputStream os = next.getContent().getOutputStream()) {
                newProps.store(os, COMMENT);
            }
            return new Integer(getVersion(next));
        } , em).intValue();
    }

}
