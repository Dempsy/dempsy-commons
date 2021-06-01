/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.distconfig.apachevfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import net.dempsy.distconfig.AutoCloseableFunction;
import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.PropertiesStore;
import net.dempsy.distconfig.TestConfigImplementation;
import net.dempsy.distconfig.apahcevfs.ApacheVfsPropertiesReader;
import net.dempsy.distconfig.apahcevfs.ApacheVfsPropertiesStore;

public class TestApacheVfsDistConfigImplementation extends TestConfigImplementation {
    private static File tmpDir = setTempDir();

    private static File setTempDir() {
        try {
            final File tmpFile = File.createTempFile("tmp", ".test");
            tmpFile.delete();
            tmpFile.mkdirs();
            tmpFile.deleteOnExit();
            return tmpFile;
        } catch(final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File genTempFile(final String testName) {
        return new File(tmpDir, "tmp-" + testName + ".properties");
    }

    @Override
    protected AutoCloseableFunction<PropertiesStore> getLoader(final String testName) {
        return new AutoCloseableFunction<PropertiesStore>() {
            private final File dir = genTempFile(testName);

            @Override
            public PropertiesStore apply(final String pfile) {
                return uncheck(() -> new ApacheVfsPropertiesStore("file://" + dir.getAbsolutePath(), pfile));
            }

            @Override
            public void close() throws Exception {
                FileUtils.deleteDirectory(dir);
            }
        };
    }

    @Override
    protected AutoCloseableFunction<PropertiesReader> getReader(final String testName) {
        return pfile -> uncheck(() -> new ApacheVfsPropertiesReader("file://" + genTempFile(testName).getAbsolutePath(), pfile));
    }

}
