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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.HexStringUtil;

/**
 * This class will load native libraries from a jar file as long as they've been packaged appropriately. 
 */
public class NativeLibraryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeLibraryLoader.class);

    public final static String libPrefix = "net.dempsy";
    public final static String propertiesFileName = libPrefix + ".lib.properties";

    public static void init() {}

    static {
        // find all of the URLs to the library properties files on the classpath
        final List<URL> iss = findAllLibProperties(propertiesFileName);

        if (iss.size() == 0) {
            // there were no properties files. Odd, someone should think there was.
            LOGGER.info("Couldn't find any " + libPrefix + " packaged native libraries.");
        } else {
            for (final URL propFile : iss) {
                LOGGER.debug("Loading native libraries from: {}", propFile);
                final Properties libProps = new Properties();

                try (InputStream propFileIs = propFile.openStream()) {
                    libProps.load(propFileIs);
                } catch (final IOException e) {
                    final String message = "Couldn't load the " + libPrefix + " native library. Couldn't load the properties out of the jar:";
                    LOGGER.error(message, e);
                    throw new UnsatisfiedLinkError(message + e.getLocalizedMessage());
                }

                LOGGER.debug("Properties for {} are: {}", propFile, libProps);

                final List<String> libs = libProps.entrySet().stream()
                        .filter(e -> {
                            final String k = ((String) e.getKey());
                            return k.startsWith("library.") || k.equals("library");
                        })
                        .map(e -> (String) e.getValue())
                        .collect(Collectors.toList());

                if (libs.size() == 0)
                    LOGGER.warn("There are no library entries in the property file {} with contents {}", propFile, libProps);

                for (final String libName : libs) {
                    final File libFile = getLibFile(libName, propFile);

                    LOGGER.debug("Loading \"{}\" as \"{}\"", libName, libFile.getAbsolutePath());
                    System.load(libFile.getAbsolutePath());
                }
            }
        }
    }

    private NativeLibraryLoader() {}

    private static MessageDigest getDigestSilent() {
        try {
            return java.security.MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException nsa) {
            final String message = "Missing MD5 algorithm.";
            LOGGER.error(message);
            throw new UnsatisfiedLinkError(message);
        }
    }

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
            final String message = "Couldn't load the library identified as the " + libPrefix + " native library (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }
    }

    private static void rethrowIOException(final Nothing<IOException> suppl, final String libName) {
        try {
            suppl.doIt();
        } catch (final IOException ioe) {
            final String message = "Couldn't load the library identified as the " + libPrefix + " native library (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }
    }

    private static File getLibFile(final String libName, final String libMD5, URL propFile) {
        final String libSuffix = libName.substring(libName.lastIndexOf('.'));

        final File tmpFile = rethrowIOException(() -> File.createTempFile(libPrefix, libSuffix), libName);

        try (Closeable tmpFileCleanup = () -> tmpFile.delete()) {
            final File tmpDir = rethrowIOException(() -> tmpFile.getParentFile(), libName);

            if (libMD5 != null) {
                final String finalFileNameOfExportedDll = libPrefix + "." + libName + "." + libMD5 + libSuffix;
                final File finalFileOfExportedDll = new File(tmpDir, finalFileNameOfExportedDll);
                // if the file is already there then we can skip reading it from the jar
                if (!finalFileOfExportedDll.exists()) {
                    // we need to copy it to the final location.
                    rethrowIOException(() -> {
                        try (InputStream is = getInputStream(libName)) {
                            FileUtils.copyInputStreamToFile(is, finalFileOfExportedDll);
                        }
                    }, libName);
                }
                return finalFileOfExportedDll;
            } else {
                LOGGER.warn("The library {} doesn't appear to have an MD5. This will be slower.");

                // otherwise we'll need to copy the file to a temp file while calculating the MD5.
                final MessageDigest digest = getDigestSilent();

                // calculate the MD5 while moving the DLL from the jar file to the temp file.
                rethrowIOException(() -> {
                    try (InputStream libIs = getInputStream(libName);) {
                    	if (libIs == null)
                    		throw new UnsatisfiedLinkError(String.format("Library \"%s\" identified from the properties \"%s\" doesn't exist in the jar file.", libName, propFile));
                    	try (InputStream is = new BufferedInputStream(new DigestInputStream(libIs, digest));) {
                        LOGGER.debug("Creating MD5 of {} using temp file: {}", libName, tmpFile);

                        FileUtils.copyInputStreamToFile(is, tmpFile);
                    }
                    }
                }, libName);

                final String md5 = HexStringUtil.bytesToHex(digest.digest());
                LOGGER.debug("MD5 of {} is: {}", libName, md5);
                final String finalFileNameOfExportedDll = libPrefix + "." + libName + "." + md5 + libSuffix;
                final File finalFileOfExportedDll = new File(tmpDir, finalFileNameOfExportedDll);

                if (finalFileOfExportedDll.exists()) {
                    LOGGER.debug("dynamic lib file {} already exists.", finalFileOfExportedDll);
                    return finalFileOfExportedDll;
                }

                // we need to use the tmp file.
                if (!tmpFile.renameTo(finalFileOfExportedDll)) {
                    final String message = "Failed to rename the library file from \"" + tmpFile.getAbsolutePath() + "\" to \""
                            + finalFileOfExportedDll.getAbsolutePath() + "\".";
                    LOGGER.error(message);
                    throw new UnsatisfiedLinkError(message);
                }

                return finalFileOfExportedDll;
            }
        } catch (final IOException ioe) {
            final String message = "Couldn't load the library identified as the " + libPrefix + " native library (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }

    }

    private static File getLibFile(final String libName, URL properties) {
        String libMD5 = null;

        // see if there's an MD5 for this already.
        try (InputStream md5Is = getInputStream(libName + ".MD5")) {
            if (md5Is != null) {
                try (final BufferedReader br = new BufferedReader(new InputStreamReader(md5Is, Charset.defaultCharset()))) {
                    libMD5 = br.readLine();
                    while (libMD5 != null && libMD5.isEmpty())
                        libMD5 = br.readLine();
                }
            }
        } catch (final IOException ioe) {
            LOGGER.debug("Failed to get md5 file for {}", libName, ioe);
            libMD5 = null;
        }

        return getLibFile(libName, libMD5, properties);
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
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            is = loader.getResourceAsStream("/" + resource);

        return is;
    }

    private static List<URL> findAllLibProperties(final String propertiesFileName) {
        final List<URL> iss = new ArrayList<>();
        try {
            final Enumeration<URL> systemResources = ClassLoader.getSystemResources(propertiesFileName);
            while (systemResources.hasMoreElements())
                iss.add(systemResources.nextElement());
        } catch (final IOException e) {
            final String message = "Couldn't load the " + libPrefix + " native library. Couldn't find the " + propertiesFileName
                    + " files on the classpath:";
            LOGGER.error(message, e);
            throw new UnsatisfiedLinkError(message + e.getLocalizedMessage());
        }
        return iss;
    }
}
