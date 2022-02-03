package net.dempsy.vfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class Test7zArchives extends BaseTest {

    @Test
    public void testTarEntryDirectToFile() throws Exception {
        try(final Vfs vfs = getVfs();) {
            final Path p = vfs.toPath(new URI("sevenz:file://" + vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath() + "!./log4j.properties"));
            assertFalse(p.isDirectory());
            try(var is = p.read();) {
                assertNotNull(IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testTarEntryDirectToDirectory() throws Exception {
        testTarEntryDirectToDirectory("./classpathReading");
        testTarEntryDirectToDirectory("./classpathReading/");
    }

    private void testTarEntryDirectToDirectory(final String pathToDir) throws Exception {

        try(final Vfs vfs = getVfs();) {
            final Path p = vfs.toPath(new URI("sevenz:file://" + vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath() + "!" + pathToDir));
            assertTrue(p.isDirectory());
            final Path[] subs = p.list();
            assertEquals(11, subs.length);
            Arrays.stream(subs)
                .forEach(u -> {
                    try {
                        assertTrue(!u.isDirectory());
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
    public void testTar() throws Exception {

        try(final Vfs vfs = getVfs();) {
            final Path p = vfs.toPath(new URI("sevenz://" + vfs.toFile(new URI("classpath:///7z.7z")).getAbsolutePath()));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    try {
                        if(!u.isDirectory()) {
                            try(var is = u.read();) {
                                assertNotNull(IOUtils.toString(is, Charset.defaultCharset()));
                            }
                        }
                    } catch(final IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                });
        }
    }
}
