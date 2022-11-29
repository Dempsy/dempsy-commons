package net.dempsy.vfs2;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.uriCompliantAbsPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.util.UriUtils;
import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

@RunWith(Parameterized.class)
public class TestTarArchives extends BaseTest {

    public TestTarArchives(final Vfs vfs) {
        super(vfs);
    }

    @Test
    public void testTarEntryDirectToFile() throws Exception {
        try(final Vfs vfs = getVfs();
            OpContext ctx = vfs.operation();) {
            final Path p = ctx
                .toPath(new URI("tar:file://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath()) + "!./log4j.properties"));
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

        try(final Vfs vfs = getVfs();
            OpContext ctx = vfs.operation()) {
            final Path p = ctx
                .toPath(new URI("tar:file://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath()) + "!" + pathToDir));
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

        try(final Vfs vfs = getVfs();
            OpContext ctx = vfs.operation();) {
            final Path p = ctx.toPath(new URI("tar://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath())));
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

    @Test
    public void testTarInTarInTar() throws Exception {
        try(final Vfs vfs = getVfs();
            OpContext ctx = vfs.operation();) {
            final String tarFile = UriUtils.uriCompliantAbsPath(vfs.toFile(new URI("classpath:///trippleTarInTar.tar")).getAbsolutePath());
            Path p = ctx.toPath(new URI("tar:file://" + tarFile));
            assertTrue(p.isDirectory());
            assertEquals(1, p.list().length);

            p = ctx.toPath(new URI("tar:tar:file://" + tarFile + "!./tar.tar"));
            assertTrue(p.isDirectory());
            assertEquals(1, p.list().length);
            p = ctx.toPath(new URI("tar:tar:tar:file://" + tarFile + "!./tar.tar!./tar.tar"));
            assertTrue(p.isDirectory());
            assertEquals(2, p.list().length);

            p = ctx.toPath(new URI("tar:tar:tar:file://" + tarFile + "!./tar.tar!./tar.tar!./classpathReading"));
            assertTrue(p.isDirectory());
            assertEquals(11, p.list().length);

            p = ctx.toPath(new URI("tar:tar:tar:file://" + tarFile + "!./tar.tar!./tar.tar!./classpathReading/test2.txt"));
            assertFalse(p.isDirectory());
            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testTarInTar() throws Exception {
        try(final Vfs vfs = getVfs();) {
            testTarInTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar.tar")).getAbsolutePath()), "./", 0);
            testTarInTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar2.tar")).getAbsolutePath()), "", 0);
            testTarInTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar3.tar")).getAbsolutePath()), "/tmp/junk/", 2);
            testTarInTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar4.tar")).getAbsolutePath()), "tmp/junk/", 2);
        }
    }

    private void testTarInTar(final Vfs vfs, final String tarFile, final String pathInTar, final int depth) throws Exception {
        try(OpContext ctx = vfs.operation();) {
            Path p = ctx.toPath(new URI("tar:file://" + tarFile));
            assertTrue(p.isDirectory());
            Path[] subs = p.list();
            if(depth != 0) {
                assertNotNull(subs);
                assertEquals(1, subs.length);
                for(int i = 0; i < depth; i++)
                    subs = subs[0].list();
            }
            assertEquals(2, subs.length);
            final Path innerTarAsFile = subs[0];
            assertFalse(innerTarAsFile.isDirectory());
            // we know it's a tar file inside a tar file so let's construct the appropriate uri
            final URI innerTarUriX = new URI("tar:" + innerTarAsFile.uri().toString());
            final URI innerTarUri = new URI("tar:tar:file://" + tarFile + "!" + pathInTar + "tar.tar");
            // the first entry should be the tar.tar file and NOT the tar.tar.gz file
            assertEquals(innerTarUri, innerTarUriX);
            final Path innerTar = ctx.toPath(innerTarUri);
            subs = innerTar.list();
            if(depth != 0) {
                assertNotNull(subs);
                assertEquals(1, subs.length);
                for(int i = 0; i < depth; i++)
                    subs = subs[0].list();
            }

            assertEquals(2, subs.length);

            Arrays.stream(subs)
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

            final String innerGzTarAsFile = "gz:tar:file://" + tarFile + "!" + pathInTar + "tar.tar.gz";
            p = ctx.toPath(new URI(innerGzTarAsFile));
            assertTrue(p.exists());
            assertFalse(p.isDirectory());

            p = ctx.toPath(new URI("tar:" + innerGzTarAsFile));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());
            subs = p.list();
            if(depth != 0) {
                assertNotNull(subs);
                assertEquals(1, subs.length);
                for(int i = 0; i < depth; i++)
                    subs = subs[0].list();
            }
            assertEquals(2, subs.length);

            // The test files are slightly inconsistent. In 3 the log4j.properties file comes before the classpathReading directory
            final int indexOfDir = subs[0].isDirectory() ? 0 : 1;
            Path indirect = subs[indexOfDir]; // classpathReading directory
            assertEquals(new URI("tar:" + innerGzTarAsFile + "!" + pathInTar + "classpathReading"), removeTrailingSlash(indirect.uri()));
            // so the other one is the log4j.properties file
            assertEquals(new URI("tar:" + innerGzTarAsFile + "!" + pathInTar + "log4j.properties"), subs[1 - indexOfDir].uri());

            p = ctx.toPath(new URI("tar:" + innerGzTarAsFile + "!" + pathInTar + "classpathReading"));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());

            p = ctx.toPath(new URI("tar:" + innerGzTarAsFile + "!" + pathInTar + "classpathReading/"));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());

            indirect = Arrays.stream(indirect.list()).filter(pa -> uncheck(() -> pa.uri()).toString().contains("test2.txt")).findAny().orElse(null);
            assertNotNull(indirect);
            p = ctx.toPath(new URI("tar:" + innerGzTarAsFile + "!" + pathInTar + "classpathReading/test2.txt"));
            assertTrue(p.exists());
            assertFalse(p.isDirectory());
            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
            p = indirect;
            assertTrue(p.exists());
            assertFalse(p.isDirectory());
            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testTarInTarDirect() throws Exception {
        try(final Vfs vfs = getVfs();) {
            testTarInTarDirect(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar.tar")).getAbsolutePath()), "./", 0);
            testTarInTarDirect(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar2.tar")).getAbsolutePath()), "", 0);
            testTarInTarDirect(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar3.tar")).getAbsolutePath()), "/tmp/junk/", 2);
            testTarInTarDirect(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar4.tar")).getAbsolutePath()), "tmp/junk/", 2);
        }
    }

    private void testTarInTarDirect(final Vfs vfs, final String tarFile, final String pathInTar, final int depth) throws Exception {
        try(OpContext ctx = vfs.operation();) {
            final String filepath = tarFile;

            Path p = ctx.toPath(new URI("file://" + filepath));
            assertFalse(p.isDirectory());

            testDirect(vfs, "file://" + filepath, pathInTar, depth);
            testDirect(vfs, "//" + filepath, pathInTar, depth);

            p = ctx.toPath(new URI("tar://" + filepath + "!" + pathInTar + "tar.tar.gz"));
            final Tika tika = new Tika();
            try(InputStream is = p.read();) {
                assertEquals("application/gzip", tika.detect(is));
            }
        }
    }

    private void testDirect(final Vfs vfs, final String fileurl, final String pathInTar, final int depth) throws Exception {
        try(OpContext ctx = vfs.operation();) {

            Path p = ctx.toPath(new URI("tar:" + fileurl));
            assertTrue(p.isDirectory());

            p = ctx.toPath(new URI("tar:" + fileurl + "!" + pathInTar + "tar.tar"));
            assertFalse(p.isDirectory());

            p = ctx.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar"));
            assertTrue(p.isDirectory());
            if(depth != 0) {
                for(int i = 0; i < depth; i++) {
                    assertEquals(1, p.list().length);
                    p = p.list()[0];
                }
            }
            assertEquals(2, p.list().length);

            p = ctx.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!"));
            assertTrue(p.isDirectory());
            if(depth != 0) {
                for(int i = 0; i < depth; i++) {
                    assertEquals(1, p.list().length);
                    p = p.list()[0];
                }
            }
            assertEquals(2, p.list().length);

            p = ctx.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!classpathReadingX"));
            assertFalse(p.exists());

            p = ctx.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!" + pathInTar + "classpathReading"));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());
            assertEquals(11, p.list().length);

            p = ctx.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!" + pathInTar + "log4j.properties"));
            assertTrue(p.exists());
            assertFalse(p.isDirectory());

            p = ctx.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!" + pathInTar + "classpathReading/test2.txt"));
            assertTrue(p.exists());
            assertFalse(p.isDirectory());

            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testTarInGzTar() throws Exception {
        try(final Vfs vfs = getVfs();
            final OpContext ctx = vfs.operation();) {
            final String file = uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar.tar")).getAbsolutePath());
            Path p;
            p = ctx.toPath(new URI("gz:tar://" + file + "!./tar.tar.gz"));
            assertFalse(p.isDirectory());
            try(var tis = p.read();) {}

            p = ctx.toPath(new URI("tar:gz:tar://" + file + "!./tar.tar.gz"));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> System.out.println("" + uncheck(() -> u.isDirectory() + ":" + u.uri())));
        }
    }

    @Test
    public void testCompressedTar() throws Exception {
        try(final Vfs vfs = getVfs();) {
            testCompressedTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///tar.tar.gz")).getAbsolutePath()), "tar:gz", "tgz");
            testCompressedTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///tar.tar.bz2")).getAbsolutePath()), "tar:bz2", "tbz2");
            testCompressedTar(vfs, uriCompliantAbsPath(vfs.toFile(new URI("classpath:///tar.tar.xz")).getAbsolutePath()), "tar:xz", "txz");
        }
    }

    private void testCompressedTar(final Vfs vfs, final String file, final String compositeScheme, final String singleScheme) throws Exception {

        testCompressedTar(vfs, singleScheme + "://" + file);
        testCompressedTar(vfs, compositeScheme + "://" + file);
    }

    private void testCompressedTar(final Vfs vfs, final String urlPrefix) throws Exception {
        try(OpContext ctx = vfs.operation();) {
            Path path;
            path = ctx.toPath(new URI(urlPrefix));
            assertTrue(path.isDirectory());
            assertEquals(2, path.list().length);

            final Path[] subs = path.list();
            path = subs[0];
            assertTrue(path.isDirectory());
            assertEquals(11, path.list().length);
            final URI uri = path.uri();
            path = ctx.toPath(new URI(urlPrefix + "!./classpathReading/"));
            assertEquals(removeTrailingSlash(uri), removeTrailingSlash(path.uri()));
            assertTrue(path.isDirectory());
            assertEquals(11, path.list().length);

            final Path text2Path = Arrays.stream(path.list())
                .filter(u -> uncheck(() -> u.uri()).toString().contains("test2.txt"))
                .findAny()
                .get();

            try(InputStream is = text2Path.read();) {
                assertTrue(text2Path.lastModifiedTime() >= 1642335069000L);
                assertTrue(text2Path.lastModifiedTime() <= (1642335069000L + 1000L));
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }

            path = ctx.toPath(new URI(urlPrefix + "!./classpathReading/test2.txt"));
            try(InputStream is = path.read();) {
                assertTrue(path.lastModifiedTime() >= 1642335069000L);
                assertTrue(path.lastModifiedTime() <= (1642335069000L + 1000L));
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }
}
