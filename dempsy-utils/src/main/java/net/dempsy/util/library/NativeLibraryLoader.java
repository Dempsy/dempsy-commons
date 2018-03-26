/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

package net.dempsy.util.library;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will load native libraries from a jar file as long as they've been packaged appropriately. 
 */
public class NativeLibraryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeLibraryLoader.class);

    private static Set<String> loaded = new HashSet<>();

    public static class Loader {
        private final List<LibraryDefinition> libs = new ArrayList<>();
        private final List<PreLibraryLoad> lls = new ArrayList<>();

        private static class LibraryDefinition {
            final private boolean required;
            final private String libName;

            private LibraryDefinition(final boolean required, final String libName) {
                super();
                this.required = required;
                this.libName = libName;
            }
        }

        public Loader library(final String... libNames) {
            Arrays.stream(libNames)
                    .map(ln -> new LibraryDefinition(true, ln))
                    .forEach(libs::add);
            return this;
        }

        public Loader optional(final String... libNames) {
            Arrays.stream(libNames)
                    .map(ln -> new LibraryDefinition(false, ln))
                    .forEach(libs::add);
            return this;
        }

        public interface PreLibraryLoad {
            public void loading(File directory, String libName, String fullLibName);
        }

        public Loader addCallback(final PreLibraryLoad ll) {
            lls.add(ll);
            return this;
        }

        public void load() {
            final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            libs.stream()
                    .filter(ld -> ld != null)
                    .filter(ld -> ld.libName != null)
                    .filter(ld -> {
                        final boolean needsLoading = !loaded.contains(ld.libName);
                        if (!needsLoading)
                            LOGGER.debug("Native library \"" + ld.libName + "\" is already loaded.");
                        return needsLoading;
                    })
                    .forEach(ld -> {
                        final String libFileName = System.mapLibraryName(ld.libName);
                        LOGGER.trace("Native library \"" + ld.libName + "\" platform specific file name is \"" + libFileName + "\"");
                        final File libFile = new File(tmpDir, libFileName);
                        final AtomicBoolean dontLoad = new AtomicBoolean(false); // need a cheap mutable boolean
                        if (!libFile.exists()) {
                            LOGGER.debug("Copying native library \"" + ld.libName + "\" from the jar file.");
                            rethrowIOException(() -> {
                                try (InputStream is = getInputStream(libFileName)) {
                                    if (is == null) {
                                        if (ld.required)
                                            throw new UnsatisfiedLinkError(
                                                    "Required native library \"" + ld.libName + "\" with platform representation \"" + libFileName
                                                            + "\" doesn't appear to exist in any jar file on the classpath");
                                        else {
                                            // if we're not required and it's missing, we're find
                                            LOGGER.debug("Requested but optional library \"" + ld.libName + "\" is not on the classpath.");
                                            dontLoad.set(true);
                                            return;
                                        }
                                    }
                                    FileUtils.copyInputStreamToFile(is, libFile);
                                }
                            }, libFileName);
                        } else
                            LOGGER.debug("Native library \"" + ld.libName + "\" is already on the filesystem. Not overwriting.");
                        if (!dontLoad.get()) {
                            lls.stream()
                                    .forEach(ll -> ll.loading(tmpDir, ld.libName, libFileName));
                            System.load(libFile.getAbsolutePath());
                        }
                        loaded.add(ld.libName);
                    });

        }
    }

    public static Loader loader() {
        return new Loader();
    }

    private NativeLibraryLoader() {}

    @FunctionalInterface
    private static interface SupplierThrows<R, E extends Throwable> {
        public R get() throws E;
    }

    @FunctionalInterface
    private static interface Nothing<E extends Throwable> {
        public void doIt() throws E;
    }

    private static <R> R rethrowIOException(final SupplierThrows<R, IOException> suppl, final String libName) {
        try {
            return suppl.get();
        } catch (final IOException ioe) {
            final String message = "Couldn't load the native library (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }
    }

    private static void rethrowIOException(final Nothing<IOException> suppl, final String libName) {
        try {
            suppl.doIt();
        } catch (final IOException ioe) {
            final String message = "Couldn't load the native library (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }
    }

    private static InputStream getInputStream(final String resource) {
        // I need to find the library. Let's start with the "current" classloader.
        // see http://www.javaworld.com/javaworld/javaqa/2003-06/01-qa-0606-load.html
        // also see: http://www.javaworld.com/javaworld/javaqa/2003-03/01-qa-0314-forname.html
        InputStream is = getInputStreamFromClassLoader(NativeLibraryLoader.class.getClassLoader(), resource);
        if (is == null) // ok, now try the context classloader
            is = getInputStreamFromClassLoader(Thread.currentThread().getContextClassLoader(), resource);
        if (is == null) // finally try the system classloader though if we're here we're probably screwed
            is = getInputStreamFromClassLoader(ClassLoader.getSystemClassLoader(), resource);

        return is;
    }

    private static InputStream getInputStreamFromClassLoader(final ClassLoader loader, final String resource) {
        if (loader == null)
            return null;
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            is = loader.getResourceAsStream("/" + resource);

        return is;
    }

}
