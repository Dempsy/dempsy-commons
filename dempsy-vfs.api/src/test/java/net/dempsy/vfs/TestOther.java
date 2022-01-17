package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.dumpUri;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TestOther {

    @Test
    public void testTar() throws Exception {
        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("tar://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath()));
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u));
        }
    }

    @Test
    public void test() throws Exception {
        try(final Vfs vfs = new Vfs();) {
            final URI uri = new URI("tar:file://" + vfs.toFile(new URI("classpath:///tar.tar")).getAbsolutePath() + "?Yo=Dude&Ftard=you");
            System.out.println(uri);
            dumpUri(uri);
            System.out.println();
            final URI iuri = new URI(uri.getSchemeSpecificPart());
            System.out.print(iuri);
            dumpUri(iuri);
        }
    }

    @Test
    public void testTarGz() throws Exception {
        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("tgz://" + vfs.toFile(new URI("classpath:///tar.tar.gz")).getAbsolutePath()));
            System.out.println(p);
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u));
        }
    }

    @Test
    public void testTarXz() throws Exception {
        try(final Vfs vfs = new Vfs();) {
            final Path p = vfs.toPath(new URI("xz://" + vfs.toFile(new URI("classpath:///tar.tar.xz")).getAbsolutePath()));
            System.out.println(p);
            assertTrue(p.isDirectory());
            Arrays.stream(p.list())
                .forEach(u -> {
                    System.out.println("" + uncheck(() -> u.isDirectory()) + ":" + u);
                });
        }
    }

}
