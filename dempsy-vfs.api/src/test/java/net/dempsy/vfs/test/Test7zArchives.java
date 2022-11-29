package net.dempsy.vfs.test;

import static net.dempsy.util.UriUtils.uriCompliantAbsPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

@RunWith(Parameterized.class)
public class Test7zArchives extends BaseTest {

    public Test7zArchives(final Supplier<Vfs> vfs) {
        super(vfs);
    }

    @Test
    public void test7zEntryDirectToFile() throws Exception {
        try(final Vfs vfs = getVfs();) {
            final Path p = vfs
                .toPath(new URI("sevenz:file://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath()) + "!log4j.properties"));
            assertFalse(p.isDirectory());
            try(var is = p.read();) {
                assertNotNull(IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void test7zEntryDirectToDirectory() throws Exception {
        test7zEntryDirectToDirectory("classpathReading");
        test7zEntryDirectToDirectory("classpathReading/");
    }

    private void test7zEntryDirectToDirectory(final String pathToDir) throws Exception {

        try(final Vfs vfs = getVfs();) {
            final Path p = vfs
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

    @Test
    public void test7z() throws Exception {
        try(final Vfs vfs = getVfs();) {
            final Path p = vfs.toPath(new URI("sevenz://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath())));
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

    @Test
    public void test7zInTar() throws Exception {
        try(final Vfs vfs = getVfs();) {
            final Path p = vfs
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
