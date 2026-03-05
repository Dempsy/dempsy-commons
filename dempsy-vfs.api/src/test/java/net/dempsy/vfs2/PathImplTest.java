package net.dempsy.vfs2;

import static net.dempsy.util.UriUtils.uriCompliantAbsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class PathImplTest {

    public static ClassPathXmlApplicationContext nfsctx;
    public static Vfs vfs;

    @BeforeAll
    public static void setupClass() {
        nfsctx = new ClassPathXmlApplicationContext("spring/vfs.xml");
        vfs = nfsctx.getBean(Vfs.class);
    }

    @AfterAll
    public static void afterClass() {
        nfsctx.close();
    }

    public static Stream<Object[]> setup() throws IOException {
        return Stream.of(
            new Object[] {"ram", null},
            new Object[] {"classpath", "classpathReading/"},
            new Object[] {"classpath", "//classpathReading/"},
            new Object[] {"classpath", "///classpathReading/"},
            new Object[] {"file", null}
        );
    }

    private String getPrefix(final String scheme, final String prefix) throws IOException {
        if(prefix == null)
            return "//" + uriCompliantAbsPath(Files.createTempDirectory("pathimpltest").toFile().getAbsolutePath()) + "/";
        return prefix;
    }

    @ParameterizedTest
    @MethodSource("setup")
    public void testToFile(final String scheme, final String prefix) throws Exception {
        final URI uri = new URI(scheme + ":" + getPrefix(scheme, prefix) + "test.txt");
        if(!"ram".equals(scheme))
            assertNotNull(vfs.toFile(uri));
        else { // expect FileSystemNotFoundException
            assertNull(vfs.toFile(uri));
        }
    }

    @ParameterizedTest
    @MethodSource("setup")
    public void testFileWriteAndReadbackAndDelete(final String scheme, final String prefix) throws Exception {
        final URI uri = new URI(scheme + ":" + getPrefix(scheme, prefix) + "test.txt");

        try(OpContext ctx = vfs.operation();) {
            final Path path = ctx.toPath(uri);
            assertEquals(uri, path.uri());

            final byte[] strBytes = "Hello World".getBytes();

            boolean canWrite = true;
            try(final OutputStream os = path.write();) {
                os.write(strBytes);
                os.flush();
            } catch(final UnsupportedOperationException uoe) {
                // this is fine for classpath
                canWrite = false;
            }

            final byte[] retBytes = new byte[strBytes.length];

            try(final InputStream inputStream = path.read()) {
                IOUtils.readFully(inputStream, retBytes);
            }

            final String results = new String(retBytes);
            assertEquals("Hello World", results);

            if(canWrite)
                path.delete();
        }
    }

    @ParameterizedTest
    @MethodSource("setup")
    public void testListContents(final String scheme, final String prefix) throws Exception {
        final byte[] strBytes = "Hello World".getBytes();
        final String resolvedPrefix = getPrefix(scheme, prefix);

        boolean canWrite = true;
        try(OpContext ctx = vfs.operation();) {
            try {
                for(int i = 0; i < 10; i++) {
                    final Path path = ctx.toPath(new URI(scheme + ":" + resolvedPrefix + "test" + i + ".txt"));

                    try(final OutputStream os = path.write();) {
                        os.write(strBytes);
                        os.flush();
                    } catch(final UnsupportedOperationException uoe) {
                        // this is fine for classpath
                        canWrite = false;
                        break;
                    }
                }

                final Path[] contents = ctx.toPath(new URI(scheme + ":" + resolvedPrefix)).list();
                assertTrue(contents.length >= 10); // it's 11 for classpath
                for(final Path path: contents) {
                    final byte[] retBytes = new byte[strBytes.length];

                    try(final InputStream inputStream = path.read()) {
                        IOUtils.readFully(inputStream, retBytes);
                    }

                    final String results = new String(retBytes);
                    assertEquals("Hello World", results);
                }
            } finally {
                if(canWrite) {
                    Exception excep = null;
                    for(int i = 0; i < 10; i++) {
                        final Path path = ctx.toPath(new URI(scheme + ":" + resolvedPrefix + "test" + i + ".txt"));
                        try {
                            path.delete();
                        } catch(final Exception e) {
                            if(excep == null)
                                excep = e;
                        }
                    }
                    if(excep != null)
                        throw excep;
                }
            }
        }
    }
}
