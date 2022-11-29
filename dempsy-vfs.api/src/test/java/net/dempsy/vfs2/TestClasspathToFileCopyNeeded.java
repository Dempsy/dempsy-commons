package net.dempsy.vfs2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class TestClasspathToFileCopyNeeded {

    @Rule public ExternalResource fakeDirOnClasspath = new ExternalResource() {

        @Override
        protected void before() throws IOException, URISyntaxException {
            // when building from maven or running in eclipse, this is in the target/test-classes directory
            try(Vfs vfs = new Vfs()) {
                final File tempFileLocation = vfs.toFile(new URI("classpath:///log4j.properties"));
                final File classpathDirectory = tempFileLocation.getParentFile();
                final File fakeDirOnClasspath = new File(classpathDirectory, "fakedir");
                if(!fakeDirOnClasspath.exists()) fakeDirOnClasspath.mkdir();
            }
        }

        @Override
        protected void after() {
            try(Vfs vfs = new Vfs()) {
                final File tempFileLocation = vfs.toFile(new URI("classpath:///log4j.properties"));
                final File classpathDirectory = tempFileLocation.getParentFile();
                final File fakeDirOnClasspath = new File(classpathDirectory, "fakedir");
                if(fakeDirOnClasspath.exists()) fakeDirOnClasspath.delete();
            } catch(final IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

    };

    @Test
    public void testCopyRequired() throws Exception {
        try(ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs.xml");) {
            final Vfs vfs = ctx.getBean(Vfs.class);
            // each Apache jar file has this file, but it doesn't exist in anything we're likely to be
            // loading up the source for.
            final File file = vfs.toFile(new URI("classpath:///META-INF/LICENSE.txt"));
            assertTrue(file.exists());
        }
    }

    @Ignore
    @Test
    public void testEmptyDirectoryFromJar() throws Exception {
        try(
            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs2.xml");) {
            final Vfs vfs = ctx.getBean(Vfs.class);

            try(OpContext oc = vfs.operation();) {
                final Path path = oc.toPath(new URI("classpath:///empty"));
                final Path[] contents = path.list();
                assertNotNull(contents);
                assertEquals(0, contents.length);

                assertTrue(path.isDirectory());
            }
        }
    }

    @Test
    public void testEmptyDirectoryOnFilesystem() throws Exception {
        try(ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs.xml");) {
            final Vfs vfs = ctx.getBean(Vfs.class);
            try(OpContext oc = vfs.operation();) {

                final Path path = oc.toPath(new URI("classpath:///fakedir"));
                final Path[] contents = path.list();
                assertNotNull(contents);
                assertEquals(0, contents.length);

                assertTrue(path.isDirectory());
            }
        }
    }

    @Test
    public void testMissingDirectory() throws Exception {
        assertThrows(FileNotFoundException.class, () -> {
            try(ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs.xml");) {
                final Vfs vfs = ctx.getBean(Vfs.class);
                try(OpContext oc = vfs.operation();) {

                    final Path path = oc.toPath(new URI("classpath:///emptyx"));
                    path.list();
                }
            }
        });
    }

}
