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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Utils {
    private static Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    public static class PropertiesBuilder {
        private final List<Properties> props = new ArrayList<>();

        public PropertiesBuilder() {}

        public PropertiesBuilder load(final InputStream is) throws IOException {
            final Properties p = new Properties();
            p.load(is);
            props.add(p);
            return this;
        }

        public PropertiesBuilder add(final String key, final String value) {
            final Properties p = new Properties();
            p.setProperty(key, value);
            props.add(p);
            return this;
        }

        public Properties build() {
            final Properties colatedProps = new Properties();
            props.stream().forEach((props) -> {
                props.forEach((key, value) -> {
                    if (LOGGER.isDebugEnabled() && colatedProps.containsKey(key)) {
                        LOGGER.debug("The property \"%s\" with the value \"%s\" is being overridden with the value \"%s\"",
                                new Object[] { key, colatedProps.getProperty((String) key), value });
                    }
                    colatedProps.setProperty((String) key, (String) value);
                });
            });
            return colatedProps;
        }

    }
}
