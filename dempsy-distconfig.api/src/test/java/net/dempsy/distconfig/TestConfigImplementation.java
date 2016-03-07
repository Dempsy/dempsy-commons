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

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import net.dempsy.distconfig.PropertiesReader.VersionedProperties;
import net.dempsy.distconfig.Utils.PropertiesBuilder;

public abstract class TestConfigImplementation {
    protected final static String PATH = "/dempsy/config";

    private static InputStream is(final String classpathResource) {
        return new BufferedInputStream(TestConfigImplementation.class.getClassLoader().getResourceAsStream(classpathResource));
    }

    protected abstract AutoCloseableFunction<PropertiesStore> getLoader(String testName) throws Exception;

    protected abstract AutoCloseableFunction<PropertiesReader> getReader(String testName) throws Exception;

    @Test
    public void testSimpleWriteAndRead() throws Exception {
        try (final AutoCloseableFunction<PropertiesStore> loaderac = getLoader("testSimpleWriteAndRead");
                final AutoCloseableFunction<PropertiesReader> readerac = getReader("testSimpleWriteAndRead");) {
            final PropertiesStore loader = loaderac.apply(PATH);
            final PropertiesReader reader = readerac.apply(PATH);
            assertEquals(0, loader.push(new PropertiesBuilder().add("hello", "world").build()));
            final VersionedProperties props = reader.read(null);
            assertEquals("world", props.getProperty("hello"));
            assertEquals(0, props.version);
        }
    }

    @Test
    public void testNotifyWithNewProps() throws Throwable {
        try (final AutoCloseableFunction<PropertiesStore> loaderac = getLoader("testNotifyWithNewProps");
                final AutoCloseableFunction<PropertiesReader> readerac = getReader("testNotifyWithNewProps");) {
            final PropertiesStore loader = loaderac.apply(PATH);
            final PropertiesReader reader = readerac.apply(PATH);

            if (reader.supportsNotification()) {

                final AtomicBoolean notified = new AtomicBoolean(false);

                // read and expect an empty properties
                assertEquals(0, reader.read(() -> notified.set(true)).size());

                // add a property.
                assertEquals(0, loader.push(new PropertiesBuilder().add("hello", "world").build()));

                if (reader.supportsNotification())
                    assertTrue(poll(notified, n -> n.get()));
            }
        }
    }

    @Test
    public void testNotifyWithPropsChangeWithMerge() throws Throwable {
        try (final AutoCloseableFunction<PropertiesStore> loaderac = getLoader("testNotifyWithPropsChangeWithMerge");
                final AutoCloseableFunction<PropertiesReader> readerac = getReader("testNotifyWithPropsChangeWithMerge");) {
            final PropertiesStore loader = loaderac.apply(PATH);
            final PropertiesReader reader = readerac.apply(PATH);

            if (reader.supportsNotification()) {
                final AtomicBoolean notifiedNew = new AtomicBoolean(false);
                final AtomicBoolean notifiedChanged = new AtomicBoolean(false);

                // read and expect an no properties
                assertEquals(0, reader.read(() -> notifiedNew.set(true)).size());

                // add a property.
                assertEquals(0, loader.push(new PropertiesBuilder().add("hello", "world").build()));

                if (reader.supportsNotification())
                    assertTrue(poll(notifiedNew, n -> n.get())); // this makes us wait until the property is set.

                // read and expect a property
                assertEquals(1, reader.read(() -> notifiedChanged.set(true)).size());

                // change a property.
                assertEquals(1, loader.push(new PropertiesBuilder().add("hello", "joe").build()));

                if (reader.supportsNotification())
                    assertTrue(poll(notifiedChanged, n -> n.get())); // this makes us wait until the property is set.

                // read and expect a property
                assertEquals(1, reader.read(null).size());

                // and that property is the new value
                assertEquals("joe", reader.read(null).getProperty("hello"));
            }
        }
    }

    @Test
    public void testNotifyWithPropsChangeWithPush() throws Throwable {
        try (final AutoCloseableFunction<PropertiesStore> loaderac = getLoader("testNotifyWithPropsChangeWithPush");
                final AutoCloseableFunction<PropertiesReader> readerac = getReader("testNotifyWithPropsChangeWithPush");) {
            final PropertiesStore loader = loaderac.apply(PATH);
            final PropertiesReader reader = readerac.apply(PATH);
            if (reader.supportsNotification()) {
                final AtomicBoolean notifiedNew = new AtomicBoolean(false);
                final AtomicBoolean notifiedChanged = new AtomicBoolean(false);

                // read and expect an no properties
                assertEquals(0, reader.read(() -> notifiedNew.set(true)).size());

                // add a property.
                assertEquals(0, loader.push(new PropertiesBuilder().add("hello", "world").add("good", "bye").build()));

                if (reader.supportsNotification())
                    assertTrue(poll(notifiedNew, n -> n.get())); // this makes us wait until the property is set.

                // read and expect a property
                assertEquals(2, reader.read(() -> notifiedChanged.set(true)).size());

                // change a property.
                assertEquals(1, loader.merge(new PropertiesBuilder().add("hello", "joe").build()));

                if (reader.supportsNotification())
                    assertTrue(poll(notifiedChanged, n -> n.get())); // this makes us wait until the property is set.

                // read and expect a property
                assertEquals(2, reader.read(null).size());

                // and that property is the new value
                assertEquals("joe", reader.read(null).getProperty("hello"));

                // and the other one still has the original value
                assertEquals("bye", reader.read(null).getProperty("good"));
            }
        }
    }

    @Test
    public void testNullWatcherNoProps() throws Exception {
        try (final AutoCloseableFunction<PropertiesReader> readerac = getReader("testNullWatcherNoProps");) {
            final PropertiesReader reader = readerac.apply(PATH);
            final VersionedProperties props = reader.read(null);
            assertEquals(-1, props.version);
            assertEquals(0, props.size());
        }
    }

    private void testPathHandling(final String testName, final String path) throws Exception {
        try (final AutoCloseableFunction<PropertiesStore> loaderac = getLoader(testName);
                final AutoCloseableFunction<PropertiesReader> readerac = getReader(testName);) {
            final PropertiesStore loader = loaderac.apply(path);
            final PropertiesReader reader = readerac.apply("/dempsy/envconf");

            final Properties loggingProps = new PropertiesBuilder().load(is("log4j.properties")).build();
            loader.push(loggingProps);
            assertEquals(loggingProps, reader.read(null));
        }
    }

    @Test
    public void testPathHandlingNoLeadingSlash() throws Throwable {
        testPathHandling("testPathHandlingNoLeadingSlash", "dempsy/envconf");
    }

    @Test
    public void testPathHandlingTrailingSlash() throws Throwable {
        testPathHandling("testPathHandlingTrailingSlash", "/dempsy/envconf/");
    }

    @Test
    public void testPathHandlingNoLeadingAndATrailingSlash() throws Throwable {
        testPathHandling("testPathHandlingNoLeadingAndATrailingSlash", "dempsy/envconf/");
    }

}
