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
import static net.dempsy.distconfig.apahcevfs.Utils.wrap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;

import net.dempsy.distconfig.PropertiesLoader;

public class ApacheVfsPropertiesLoader extends PropertiesLoader {

    private final FileObject parentDirObj;
    private final static String COMMENT = "These properties loaded using " + ApacheVfsPropertiesLoader.class.getSimpleName();

    public ApacheVfsPropertiesLoader(final String parentUri, final String childPropertiesName) throws IOException {
        final FileObject baseDir = wrap(() -> VFS.getManager().resolveFile(parentUri));
        parentDirObj = wrap(() -> VFS.getManager().resolveFile(baseDir, cleanPath(childPropertiesName)));
    }

    public ApacheVfsPropertiesLoader(final String pathUri) throws IOException {
        parentDirObj = wrap(() -> VFS.getManager().resolveFile(pathUri));
    }

    @Override
    public int push(final Properties props) throws IOException {
        return wrap(() -> {
            final FileObject next = nextFile(getLatest(parentDirObj), parentDirObj);
            try (OutputStream os = next.getContent().getOutputStream()) {
                props.store(os, COMMENT);
            }
            return new Integer(getVersion(next));
        }).intValue();
    }

    @Override
    public int merge(final Properties props) throws IOException {
        return wrap(() -> {
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
        }).intValue();
    }

}
