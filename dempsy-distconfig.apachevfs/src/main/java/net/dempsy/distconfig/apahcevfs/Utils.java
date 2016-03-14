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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.BinaryOperator;

import org.apache.commons.vfs2.FileNotFolderException;
import org.apache.commons.vfs2.FileNotFoundException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;

class Utils {
    public static final String versionSuffixPrefix = "v_";
    public static final int versionSuffixPrefixLen = versionSuffixPrefix.length();

    public static String cleanPath(final String path) {
        String trimmed = path.trim();
        if (trimmed.startsWith("/"))
            trimmed = cleanPath(trimmed.substring(1, trimmed.length()));
        if (trimmed.endsWith("/"))
            trimmed = trimmed.substring(0, trimmed.length() - 1); // chop off trailing '/'. Let's hope there's not more than one.
        return trimmed;
    }

    public static String genVersionSuffix(final int version) {
        return String.format("%s%010d", versionSuffixPrefix, version);
    }

    private final static BinaryOperator<FileObject> findLatest = (final FileObject prev, final FileObject cur) -> prev == null ? cur
            : (prev.getName().getBaseName().compareTo(cur.getName().getBaseName()) > 0 ? prev : cur);

    public static FileObject getLatest(final FileObject parent) throws FileSystemException {
        try {
            final FileObject[] children = parent.getChildren();
            if (children == null || children.length == 0)
                return null;
            return Arrays.stream(children).reduce(null, findLatest, findLatest);
        } catch (final FileNotFoundException | FileNotFolderException afnfe) {
            if (parent.exists())
                throw afnfe;
            return null;
        }
    }

    public static int getVersion(final FileObject file) {
        final String base = file.getName().getBaseName();
        return Integer.parseInt(base.substring(versionSuffixPrefixLen, base.length()));
    }

    public static FileObject nextFile(final FileObject lastFile, final FileObject parent) throws FileSystemException {
        if (lastFile == null)
            return parent.resolveFile(genVersionSuffix(0));
        final int lastVersion = getVersion(lastFile);
        return lastFile.getParent().resolveFile(genVersionSuffix(lastVersion + 1));
    }

    public static Properties read(final FileObject file) throws IOException {
        final Properties props = new Properties();
        if (file == null)
            return props;
        boolean isopen = false;
        try (InputStream is = file.getContent().getInputStream()) {
            isopen = true;
            props.load(is);
        } catch (final FileSystemException fse) {
            if (isopen)
                throw fse;
            // otherwise we assume the file didn't exist ... which is fine.
        }
        return props;
    }
}
