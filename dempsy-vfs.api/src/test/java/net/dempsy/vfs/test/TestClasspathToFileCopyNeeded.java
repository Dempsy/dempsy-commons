package net.dempsy.vfs.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class TestClasspathToFileCopyNeeded {

    @BeforeEach
    public void setupFakeDirOnClasspath() throws IOException, URISyntaxException {
        // when building from maven or running in eclipse, this is in the target/test-classes directory
        try(Vfs vfs = new Vfs()) {
            final File tempFileLocation = vfs.toFile(new URI("classpath:///logback-test.xml"));
            final File classpathDirectory = tempFileLocation.getParentFile();
            final File fakeDirOnClasspath = new File(classpathDirectory, "fakedir");
            if(!fakeDirOnClasspath.exists()) fakeDirOnClasspath.mkdir();
        }
    }

    @AfterEach
    public void teardownFakeDirOnClasspath() {
        try(Vfs vfs = new Vfs()) {
            final File tempFileLocation = vfs.toFile(new URI("classpath:///logback-test.xml"));
            final File classpathDirectory = tempFileLocation.getParentFile();
            final File fakeDirOnClasspath = new File(classpathDirectory, "fakedir");
            if(fakeDirOnClasspath.exists()) fakeDirOnClasspath.delete();
        } catch(final IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

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

    @Test
    @Disabled
    public void testEmptyDirectoryFromJar() throws Exception {
        try(ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs.xml");) {
            final Vfs vfs = ctx.getBean(Vfs.class);

            final Path path = vfs.toPath(new URI("classpath:///empty"));
            final Path[] contents = path.list();
            assertNotNull(contents);
            assertEquals(0, contents.length);

            assertTrue(path.isDirectory());
        }
    }

    @Test
    public void testEmptyDirectoryOnFilesystem() throws Exception {
        try(ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs.xml");) {
            final Vfs vfs = ctx.getBean(Vfs.class);
            System.out.println(Arrays.toString(vfs.supportedSchemes()));

            final Path path = vfs.toPath(new URI("classpath:///fakedir"));
            final Path[] contents = path.list();
            assertNotNull(contents);
            assertEquals(0, contents.length);

            assertTrue(path.isDirectory());
        }
    }

    @Test
    public void testMissingDirectory() throws Exception {
        assertThrows(FileNotFoundException.class, () -> {
            try(ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring/vfs.xml");) {
                final Vfs vfs = ctx.getBean(Vfs.class);
                final Path path = vfs.toPath(new URI("classpath:///emptyx"));
                path.list();
            }
        });
    }

}
