package net.dempsy.vfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import static net.dempsy.util.UriUtils.*;

@RunWith(Parameterized.class)
public class PathImplTest {
	
	@Rule
	public TemporaryFolder tmpdir = new TemporaryFolder();

    public static ClassPathXmlApplicationContext nfsctx;
    public static Vfs vfs;

    @BeforeClass
    public static void setupClass() {
        nfsctx = new ClassPathXmlApplicationContext("spring/vfs.xml");
        vfs = nfsctx.getBean(Vfs.class);
    }

    @AfterClass
    public static void afterClass() {
        nfsctx.close();
    }

    @Parameters
    public static Collection<Object[]> setup() throws IOException {

        return Arrays.<Object[]>asList(

            new Object[] {"ram", null },
            new Object[] {"classpath","classpathReading/"},
            new Object[] {"classpath","//classpathReading/"},
            new Object[] {"classpath","///classpathReading/"},
            new Object[] {"file", null }
            		
            );
    }

    private final String scheme;
    private final String prefix;

    public PathImplTest(final String scheme, final String prefix) {
        this.scheme = scheme;
        this.prefix = prefix;
    }

    private String getPrefix() throws IOException {
    	if (prefix == null)
    		return "//" + uriCompliantAbsPath(tmpdir.newFolder().getAbsolutePath()) + "/";
    	return prefix;
    }
    @Test
    public void testToFile() throws Exception {
        final URI uri = new URI(scheme + ":" + getPrefix() + "test.txt");
        if(!"ram".equals(scheme))
            assertNotNull(vfs.toFile(uri));
        else { // expect FileSystemNotFoundException
            boolean caught = false;
            try {
                vfs.toFile(uri);
            } catch(final FileSystemNotFoundException fsnfe) {
                caught = true;
            }
            assertTrue(caught);
        }
    }

    @Test
    public void testFileWriteAndReadbackAndDelete() throws Exception {
        final URI uri = new URI(scheme + ":" + getPrefix() + "test.txt");
        final Path path = vfs.toPath(uri);

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

    @Test
    public void testListContents() throws Exception {
        final byte[] strBytes = "Hello World".getBytes();
        String prefix = getPrefix();
        
        boolean canWrite = true;
        try {
            for(int i = 0; i < 10; i++) {
                final Path path = vfs.toPath(new URI(scheme + ":" + prefix + "test" + i + ".txt"));

                try(final OutputStream os = path.write();) {
                    os.write(strBytes);
                    os.flush();
                } catch(final UnsupportedOperationException uoe) {
                    // this is fine for classpath
                    canWrite = false;
                    break;
                }
            }

            final Path[] contents = vfs.toPath(new URI(scheme + ":" + prefix)).list();
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
                    final Path path = vfs.toPath(new URI(scheme + ":" + prefix + "test" + i + ".txt"));
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
