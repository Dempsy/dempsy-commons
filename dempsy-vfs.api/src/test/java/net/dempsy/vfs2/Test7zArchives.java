package net.dempsy.vfs2;

import static net.dempsy.util.UriUtils.uriCompliantAbsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class Test7zArchives extends BaseTest {

    @ParameterizedTest
    @MethodSource("vfsConfigs")
    public void test7zEntryDirectToFile(final Vfs vfs) throws Exception {
        try(OpContext oc = vfs.operation();) {
            final Path p = oc
                .toPath(new URI("sevenz:file://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath()) + "!log4j.properties"));
            assertFalse(p.isDirectory());
            try(var is = p.read();) {
                assertNotNull(IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("vfsConfigs")
    public void test7zEntryDirectToDirectory(final Vfs vfs) throws Exception {
        test7zEntryDirectToDirectory(vfs, "classpathReading");
        test7zEntryDirectToDirectory(vfs, "classpathReading/");
    }

    private void test7zEntryDirectToDirectory(final Vfs vfs, final String pathToDir) throws Exception {

        try(OpContext oc = vfs.operation();) {
            final Path p = oc
                .toPath(new URI("sevenz:file://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath()) + "!" + pathToDir));
            assertTrue(p.isDirectory());
            final Path[] subs = p.list();
            assertEquals(11, subs.length);
            Arrays.stream(subs)
                .forEach(u -> {
                    try {
                        assertTrue(!u.isDirectory());
                        assertTrue(u.lastModifiedTime() >= 1642335069000L);
                        assertTrue(u.lastModifiedTime() <= (1642335069000L + 1000L));
                        try(var is = u.read();) {
                            assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
                        }
                    } catch(final IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                });
        }
    }

    @ParameterizedTest
    @MethodSource("vfsConfigs")
    public void test7z(final Vfs vfs) throws Exception {
        try(OpContext oc = vfs.operation();) {
            final Path p = oc.toPath(new URI("sevenz://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath())));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    try {
                        if(!u.isDirectory()) {
                            try(var is = u.read();) {
                                assertNotNull(IOUtils.toString(is, Charset.defaultCharset()));
                            }
                        } else {
                            // classpathReading directory
                            final Path[] txts = u.list();
                            assertEquals(11, txts.length);
                            for(final Path txt: txts) {
                                assertTrue(txt.lastModifiedTime() >= 1642335069000L);
                                assertTrue(txt.lastModifiedTime() <= (1642335069000L + 1000L));
                                try(var iis = txt.read();) {
                                    assertEquals("Hello World", IOUtils.toString(iis, Charset.defaultCharset()));
                                }
                            }
                        }
                    } catch(final IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                });
        }
    }

    @ParameterizedTest
    @MethodSource("vfsConfigs")
    public void test7zInTar(final Vfs vfs) throws Exception {
        try(OpContext oc = vfs.operation();) {
            final Path p = oc
                .toPath(new URI("sevenz:tar://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///7z.7z.tar")).getAbsolutePath()) + "!./7z.7z"));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    try {
                        if(!u.isDirectory()) {
                            try(var is = u.read();) {
                                assertNotNull(IOUtils.toString(is, Charset.defaultCharset()));
                            }
                        } else {
                            // classpathReading directory
                            final Path[] txts = u.list();
                            assertEquals(11, txts.length);
                            for(final Path txt: txts) {
                                assertTrue(txt.lastModifiedTime() >= 1642335069000L);
                                assertTrue(txt.lastModifiedTime() <= (1642335069000L + 1000L));
                                try(var iis = txt.read();) {
                                    assertEquals("Hello World", IOUtils.toString(iis, Charset.defaultCharset()));
                                }
                            }
                        }
                    } catch(final IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                });
        }
    }
}
