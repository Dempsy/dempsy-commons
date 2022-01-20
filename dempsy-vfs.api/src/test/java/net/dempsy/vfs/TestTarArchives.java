package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

public class TestTarArchives {

    @Test
    public void testTarEntryDirectToFile() throws Exception {

        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("tar:file://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath() + "!log4j.properties"));
            assertFalse(p.isDirectory());
            try(var is = p.read();) {
                System.out.println(IOUtils.toString(is, Charset.defaultCharset()));
            }
        }
    }

    @Test
    public void testTarEntryDirectToDirectory() throws Exception {

        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("tar:file://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath() + "!classpathReading"));
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

        try(final Vfs vfs = new Vfs();) {
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

    @Ignore
    @Test
    public void testTarInTar() throws Exception {

        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("tar:file://" + vfs.toFile(new URI("classpath:///simpleTarInTar.tar")).getAbsolutePath()));
            assertTrue(p.isDirectory());
            final Path[] subs = p.list();
            assertEquals(1, subs.length);
            final Path innerTarAsFile = subs[0];
            // assertTrue(innerTar.isDirectory());
            // we know it's a tar file inside a tar file so let's construct the appropriate uri
            final URI innerTarUri = new URI("tar:" + innerTarAsFile.uri().toString());
            System.out.println(innerTarUri);
            final Path innerTar = vfs.toPath(innerTarUri);

            Arrays.stream(innerTar.list())
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
    public void testTarGz() throws Exception {
        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("tar:gz://" + vfs.toFile(new URI("classpath:///tar.tar.gz")).getAbsolutePath()));
            System.out.println(p);
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u.uri()));
        }
    }

    @Test
    public void testTarXz() throws Exception {
        try(final Vfs vfs = new Vfs();) {
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
