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

package net.dempsy.distconfig.classpath;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.util.Properties;

import net.dempsy.distconfig.AutoCloseableFunction;
import net.dempsy.distconfig.PropertiesReader;
import net.dempsy.distconfig.PropertiesStore;
import net.dempsy.distconfig.TestConfigImplementation;

public class TestClasspathDistConfigImplementation extends TestConfigImplementation {

    @Override
    protected AutoCloseableFunction<PropertiesStore> getLoader(final String testName) {
        return new AutoCloseableFunction<PropertiesStore>() {

            @Override
            public PropertiesStore apply(final String pfile) {
                // stub out the store.
                return new PropertiesStore() {

                    @Override
                    public int push(final Properties props) throws IOException {
                        return 0;
                    }

                    @Override
                    public int merge(final Properties props) throws IOException {
                        return 0;
                    }

                    @Override
                    public int clear(final String... propNames) throws IOException {
                        return 0;
                    }
                };
            }

            @Override
            public void close() throws Exception {}
        };
    }

    @Override
    protected AutoCloseableFunction<PropertiesReader> getReader(final String testName) {
        return !"testNullWatcherNoProps".equals(testName) ? (path -> uncheck(() -> new ClasspathPropertiesReader(path)))
                : (path -> uncheck(() -> new ClasspathPropertiesReader("nofile")));
    }

}
