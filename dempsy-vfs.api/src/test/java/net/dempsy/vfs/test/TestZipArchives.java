package net.dempsy.vfs.test;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.uriCompliantAbsPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.util.UriUtils;
import net.dempsy.vfs2.Path;
import net.dempsy.vfs2.Vfs;

@RunWith(Parameterized.class)
public class TestZipArchives extends BaseTest {

    public TestZipArchives(final Supplier<Vfs> vfs) {
        super(vfs);
    }

    private void assertMime(final String mime, final Path p) throws Exception {
        final Tika tika = new Tika();
        try(InputStream is = p.read();) {
            assertEquals(mime, tika.detect(is));
        }
    }

    @Test
    public void testRecursiveCompression() throws Exception {
        try(final Vfs vfs = getVfs();) {
            String file = UriUtils.sanitize(uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleTarInTar.tar.gz.bz2.Z.gz.gz.gz")).getAbsolutePath()))
                .toString();

            Path p = vfs.toPath(new URI(file));
            assertTrue(p.exists());

            file = "gz:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertMime("application/gzip", p);

            file = "gz:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertMime("application/gzip", p);

            file = "gz:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertMime("application/x-compress", p);

            file = "Z:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertMime("application/x-bzip2", p);

            file = "bz2:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertMime("application/gzip", p);

            file = "gz:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertMime("application/x-gtar", p);

            file = "tar:" + file;
            p = vfs.toPath(new URI(file));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());

            p = vfs.toPath(new URI(file + "!./tar.tar"));
            assertTrue(p.exists());
            assertMime("application/x-gtar", p);

            p = vfs.toPath(new URI(file + "!./tar.tar.gz"));
            assertTrue(p.exists());
            assertMime("application/gzip", p);

            p = vfs.toPath(new URI("gz:" + file + "!./tar.tar.gz"));
            assertTrue(p.exists());
            assertMime("application/x-gtar", p);

            p = vfs.toPath(new URI("tar:gz:" + file + "!./tar.tar.gz"));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());
            assertEquals(2, p.list().length);

            p = p.list()[0];
            assertTrue(p.exists());
            assertTrue(p.isDirectory());
            assertEquals(11, p.list().length);

            p = Arrays.stream(p.list())
                .filter(u -> uncheck(() -> u.uri()).toString().contains("test2.txt"))
                .findAny()
                .get();
            assertTrue(p.exists());
            assertFalse(p.isDirectory());
            assertTrue(p.lastModifiedTime() >= 1642335069000L);
            assertTrue(p.lastModifiedTime() <= (1642335069000L + 1000L));
            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }

            final String tmps = p.uri().toString();
            final String junk = "tar:gz:" + file + "!./tar.tar.gz!./classpathReading/test2.txt";
            assertEquals(tmps, junk);
            p = vfs.toPath(new URI(junk));
            assertEquals(tmps, p.uri().toString());
            assertTrue(p.exists());
            assertFalse(p.isDirectory());
            assertTrue(p.lastModifiedTime() >= 1642335069000L);
            assertTrue(p.lastModifiedTime() <= (1642335069000L + 1000L));
            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testZip() throws Exception {

        try(final Vfs vfs = getVfs();) {
            final Path p = vfs.toPath(new URI("zip://" + uriCompliantAbsPath(vfs.toFile(new URI("classpath:///simpleZip.zip")).getAbsolutePath())));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + uncheck(() -> u.uri()));

                    try {
                        if(!u.isDirectory()) {
                            try(var is = u.read();) {
                                System.out.println(IOUtils.toString(is, Charset.defaultCharset()));
                            }
                        }
                    } catch(final IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                });
        }
    }

    @Test
    public void testZipWithNonAsciiEncodedFilename() throws Exception {
        try(final Vfs vfs = getVfs();) {
            final String file = uriCompliantAbsPath(vfs.toFile(new URI("classpath:///encoding.zip")).getAbsolutePath());
            Path p = vfs.toPath(new URI("zip://" + file));
            assertTrue(p.exists());
            assertTrue(p.isDirectory());
            assertEquals(1, p.list().length);
            String name = UriUtils.getName(p.list()[0].uri());
            // without a root directory '/' UriUtils.getName will leave the ENC on the uri
            name = name.substring(name.indexOf('!') + 1);
            assertEquals("Français.txt", name);
            p = vfs.toPath(new URI("zip://" + file + "!" + name));
            assertTrue(p.exists());
            assertFalse(p.isDirectory());
            try(InputStream is = p.read();) {
                assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testZipInTar() throws Exception {

        try(final Vfs vfs = getVfs();) {
            final String file = uriCompliantAbsPath(vfs.toFile(new URI("classpath:///zipInTar.tar")).getAbsolutePath());

            Path p;

            p = vfs.toPath(new URI("tar://" + file));
            assertTrue(p.isDirectory());
            assertEquals(1, p.list().length);

            p = vfs.toPath(new URI("zip:tar://" + file + tenc + "./simpleZip.zip"));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list()).map(pa -> uncheck(() -> pa.uri())).forEach(u -> System.out.println(u));
            assertEquals(2, p.list().length);

            p = vfs.toPath(new URI("zip:tar://" + file + tenc + "./simpleZip.zip" + zenc + "classpathReading/"));
            assertTrue(p.isDirectory());
            assertEquals(11, p.list().length);
        }
    }

}
