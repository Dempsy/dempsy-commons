package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;
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

public class TestTarArchives {

    @Test
    public void testTarEntryDirectToFile() throws Exception {
        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            final Path p = vfs.toPath(new URI("tar:file://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath() + "!./log4j.properties"));
            assertFalse(p.isDirectory());
            try(var is = p.read();) {
                System.out.println(IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testTarEntryDirectToDirectory() throws Exception {
        testTarEntryDirectToDirectory("./classpathReading");
        testTarEntryDirectToDirectory("./classpathReading/");
    }

    private void testTarEntryDirectToDirectory(final String pathToDir) throws Exception {

        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            final Path p = vfs.toPath(new URI("tar:file://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath() + "!" + pathToDir));
            assertTrue(p.isDirectory());
            final Path[] subs = p.list();
            assertEquals(11, subs.length);
            Arrays.stream(subs)
                .forEach(u -> {
                    System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u.uri());

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

        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            final Path p = vfs.toPath(new URI("tar://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath()));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u.uri());

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
    public void testTarInTar() throws Exception {
        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            testTarInTar(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar.tar")).getAbsolutePath(), "./", 0);
            testTarInTar(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar2.tar")).getAbsolutePath(), "", 0);
            testTarInTar(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar3.tar")).getAbsolutePath(), "/tmp/junk/", 2);
            testTarInTar(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar4.tar")).getAbsolutePath(), "tmp/junk/", 2);
        }
    }

    private void testTarInTar(final Vfs vfs, final String tarFile, final String pathInTar, final int depth) throws Exception {
        final Path p = vfs.toPath(new URI("tar:file://" + tarFile));
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
        System.out.println(innerTarUriX);
        final URI innerTarUri = new URI("tar:tar:file://" + tarFile + "!" + pathInTar + "tar.tar");
        System.out.println(innerTarUri);
        // the first entry should be the tar.tar file and NOT the tar.tar.gz file
        assertEquals(innerTarUri, innerTarUriX);
        final Path innerTar = vfs.toPath(innerTarUri);
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
                System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u.uri());

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

    @Test
    public void testTarInTarDirect() throws Exception {
        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            testTarInTarDirect(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar.tar")).getAbsolutePath(), "./", 0);
            testTarInTarDirect(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar2.tar")).getAbsolutePath(), "", 0);
            testTarInTarDirect(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar3.tar")).getAbsolutePath(), "/tmp/junk/", 2);
            testTarInTarDirect(vfs, vfs.toFile(new URI("classpath:///simpleTarInTar4.tar")).getAbsolutePath(), "tmp/junk/", 2);
        }
    }

    private void testTarInTarDirect(final Vfs vfs, final String tarFile, final String pathInTar, final int depth) throws Exception {
        final String filepath = tarFile;

        Path p = vfs.toPath(new URI("file://" + filepath));
        assertFalse(p.isDirectory());

        testDirect(vfs, "file://" + filepath, pathInTar, depth);
        testDirect(vfs, "//" + filepath, pathInTar, depth);

        p = vfs.toPath(new URI("tar://" + filepath + "!" + pathInTar + "tar.tar.gz"));
        final Tika tika = new Tika();
        try(InputStream is = p.read();) {
            assertEquals("application/gzip", tika.detect(is));
        }
    }

    private void testDirect(final Vfs vfs, final String fileurl, final String pathInTar, final int depth) throws Exception {

        Path p = vfs.toPath(new URI("tar:" + fileurl));
        assertTrue(p.isDirectory());

        p = vfs.toPath(new URI("tar:" + fileurl + "!" + pathInTar + "tar.tar"));
        assertFalse(p.isDirectory());

        p = vfs.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar"));
        assertTrue(p.isDirectory());
        if(depth != 0) {
            for(int i = 0; i < depth; i++) {
                assertEquals(1, p.list().length);
                p = p.list()[0];
            }
        }
        assertEquals(2, p.list().length);

        p = vfs.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!"));
        assertTrue(p.isDirectory());
        if(depth != 0) {
            for(int i = 0; i < depth; i++) {
                assertEquals(1, p.list().length);
                p = p.list()[0];
            }
        }
        assertEquals(2, p.list().length);

        p = vfs.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!classpathReadingX"));
        assertFalse(p.exists());

        p = vfs.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!" + pathInTar + "classpathReading"));
        assertTrue(p.exists());
        assertTrue(p.isDirectory());
        assertEquals(11, p.list().length);

        p = vfs.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!" + pathInTar + "log4j.properties"));
        assertTrue(p.exists());
        assertFalse(p.isDirectory());

        p = vfs.toPath(new URI("tar:tar:" + fileurl + "!" + pathInTar + "tar.tar!" + pathInTar + "classpathReading/test2.txt"));
        assertTrue(p.exists());
        assertFalse(p.isDirectory());

        try(InputStream is = p.read();) {
            assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
        }
    }

    @Test
    public void testTarGz() throws Exception {
        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            final Path p = vfs.toPath(new URI("tar:gz://" + vfs.toFile(new URI("classpath:///tar.tar.gz")).getAbsolutePath()));
            System.out.println(p);
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u.uri()));
        }
    }

    @Test
    public void testTarXz() throws Exception {
        try(final Vfs vfs = new Vfs(new TarFileSystem());) {
            final Path p = vfs.toPath(new URI("tar:xz://" + vfs.toFile(new URI("classpath:///tar.tar.xz")).getAbsolutePath()));
            System.out.println(p);
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u);
                });
        }
    }
}
