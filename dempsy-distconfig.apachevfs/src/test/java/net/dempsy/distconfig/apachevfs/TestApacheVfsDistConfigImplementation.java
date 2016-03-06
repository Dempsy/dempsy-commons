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

package net.dempsy.distconfig.apachevfs;

import java.io.File;
import java.io.IOException;

import org.codehaus.plexus.util.FileUtils;

import net.dempsy.distconfig.AutoCloseableFunction;
import net.dempsy.distconfig.PropertiesLoader;
import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.TestConfigImplementation;
import net.dempsy.distconfig.apahcevfs.ApacheVfsPropertiesLoader;
import net.dempsy.distconfig.apahcevfs.ApacheVfsPropertiesReader;

public class TestApacheVfsDistConfigImplementation extends TestConfigImplementation {
    private static File tmpDir = setTempDir();

    private static File setTempDir() {
        try {
            final File tmpFile = File.createTempFile("tmp", ".test");
            tmpFile.delete();
            tmpFile.mkdirs();
            tmpFile.deleteOnExit();
            return tmpFile;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File genTempFile(final String testName) {
        return new File(tmpDir, "tmp-" + testName + ".properties");
    }

    @FunctionalInterface
    public static interface SupplierThrows<T> {
        T get() throws Exception;
    }

    public static <T> T unchecked(final SupplierThrows<T> f) {
        try {
            return f.get();
        } catch (final Exception fse) {
            throw new RuntimeException(fse);
        }
    }

    @Override
    protected AutoCloseableFunction<PropertiesLoader> getLoader(final String testName) {
        return new AutoCloseableFunction<PropertiesLoader>() {
            private final File dir = genTempFile(testName);

            @Override
            public PropertiesLoader apply(final String pfile) {
                return unchecked(() -> new ApacheVfsPropertiesLoader("file://" + dir.getAbsolutePath(), pfile));
            }

            @Override
            public void close() throws Exception {
                FileUtils.deleteDirectory(dir);
            }
        };
    }

    @Override
    protected AutoCloseableFunction<PropertiesReader> getReader(final String testName) {
        return pfile -> unchecked(() -> new ApacheVfsPropertiesReader("file://" + genTempFile(testName).getAbsolutePath(), pfile));
    }

}
