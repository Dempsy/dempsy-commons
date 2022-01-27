package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.encodePath;
import static net.dempsy.util.UriUtils.prependScheme;
import static net.dempsy.util.UriUtils.resolve;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import net.dempsy.util.UriUtils;

public class TestOther extends BaseTest {

    @Test
    public void testCrazyNestedWithBadChars() throws Exception {

        try(final Vfs vfs = getVfs();) {
            final File tDir = vfs.toFile(new URI("classpath:///"));

            final String fn = "tar In Bla Bla Bla.tar.bz2";
            final String fnEncoded = encodePath(fn);
            final String fullPathToFnEnc = new File(tDir, fnEncoded).getAbsolutePath();

            final File outerFile = new File(tDir, "tar In Bla Bla Bla.tar.bz2");
            assertTrue(outerFile.exists());

            Path tmp;
            Path p = vfs.toPath(outerFile.toURI());
            assertTrue(p.exists());
            assertFalse(p.isDirectory());

            {
                // direct
                tmp = vfs.toPath(new URI("bz2://" + fullPathToFnEnc));
                assertTrue(tmp.exists());
                assertFalse(tmp.isDirectory());

                // prepend outer scheme onto dir uri
                p = vfs.toPath(prependScheme("bz2", p.uri()));
                assertTrue(p.exists());
                assertFalse(p.isDirectory());

                // prepend outer scheme onto uri from the file dir
                p = vfs.toPath(prependScheme("bz2", resolve(tDir.toURI(), fn)));
                assertTrue(p.exists());
                assertFalse(p.isDirectory());
            }

            {
                // direct
                tmp = vfs.toPath(new URI("tar:bz2://" + fullPathToFnEnc));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);

                // prepend outer scheme onto prev final result
                p = vfs.toPath(prependScheme("tar", p.uri()));
                assertTrue(p.exists());
                assertTrue(p.isDirectory());
                assertEquals(1, p.list().length);
            }
            {
                // the subdirectory is "." (or "./").
                // direct
                tmp = vfs.toPath(new URI("tar:bz2://" + fullPathToFnEnc + "!./"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);
                tmp = vfs.toPath(new URI("tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar"));
                assertTrue(tmp.exists());
                assertFalse(tmp.isDirectory());

                // access via recusion
                tmp = p;
                p = p.list()[0];
                assertTrue(p.exists());
                assertFalse(p.isDirectory());

                // access via resolve
                tmp = vfs.toPath(resolve(tmp.uri(), "tar In Zip In `tar.tar"));
                assertTrue(tmp.exists());
                assertFalse(tmp.isDirectory());
            }

            {
                // the subdirectory is "." (or "./").
                // direct
                tmp = vfs.toPath(new URI("tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);

                // prepend outer scheme onto prev final result
                p = vfs.toPath(prependScheme("tar", p.uri()));
                assertTrue(p.exists());
                assertTrue(p.isDirectory());
                assertEquals(1, p.list().length);
            }

            {
                // the subdirectory is "." (or "./").
                // direct
                tmp = vfs.toPath(new URI("tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);
                tmp = vfs.toPath(new URI("tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip"));
                assertTrue(tmp.exists());
                assertFalse(tmp.isDirectory());

                // access via recusion
                tmp = p;
                p = p.list()[0];
                assertTrue(p.exists());
                assertFalse(p.isDirectory());

                // resolve doesn't work because the resulting uri contains a / between the
                // uri and the "tarInZip.zip"
                // ... tar.bz2!./tar%20In%20Zip%20In%20%60tar.tar!/tarInZip.zip
                // where the tar file is constructed without the slash like this:
                // ... tar.bz2!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip
            }

            {
                // the subdirectory is "." (or "./").
                // direct
                tmp = vfs.toPath(new URI("zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);

                // prepend outer scheme onto prev final result
                p = vfs.toPath(prependScheme("zip", p.uri()));
                assertTrue(p.exists());
                assertTrue(p.isDirectory());
                assertEquals(1, p.list().length);
            }

            {
                // direct
                tmp = vfs.toPath(new URI("zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);
                tmp = vfs.toPath(
                    new URI("zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip" + ZipFileSystem.ENC + "other.tar.gz"));
                assertTrue(tmp.exists());
                assertFalse(tmp.isDirectory());
                final URI expected = tmp.uri();

                // access via recusion
                tmp = p;
                p = p.list()[0];
                assertTrue(p.exists());
                assertFalse(p.isDirectory());

                assertEquals(expected, p.uri());

                // resolve doesn't work because the resulting uri contains a / between the
                // uri and the "tarInZip.zip"
                // ... tar.bz2!./tar%20In%20Zip%20In%20%60tar.tar!/tarInZip.zip
                // where the tar file is constructed without the slash like this:
                // ... tar.bz2!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip
            }

            {
                // the subdirectory is "." (or "./").
                // direct
                tmp = vfs.toPath(new URI(
                    "gz:zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip" + ZipFileSystem.ENC + "other.tar.gz"));
                assertTrue(tmp.exists());
                assertFalse(tmp.isDirectory());

                // prepend outer scheme onto prev final result
                p = vfs.toPath(prependScheme("gz", p.uri()));
                assertTrue(p.exists());
                assertFalse(p.isDirectory());
            }

            {
                // the subdirectory is "." (or "./").
                // direct
                tmp = vfs.toPath(new URI(
                    "tar:gz:zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip" + ZipFileSystem.ENC + "other.tar.gz"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());
                assertEquals(1, tmp.list().length);
                final URI expected = tmp.uri();
                tmp = vfs.toPath(new URI(
                    "tar:gz:zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip" + ZipFileSystem.ENC
                        + "other.tar.gz!other/"));
                assertTrue(tmp.exists());
                assertTrue(tmp.isDirectory());

                // access via recusion
                p = vfs.toPath(prependScheme("tar", p.uri()));
                assertTrue(p.exists());
                assertTrue(p.isDirectory());
                assertEquals(1, p.list().length);

                assertEquals(expected, p.uri());
                p = p.list()[0];
                assertEquals(tmp.uri(), p.uri());

                testBadCharsPath(vfs, p, "dir With Brackets[square]", p.uri());
                testBadCharsPath(vfs, tmp, "dir With Brackets[square]", tmp.uri());

                testBadCharsPath(vfs, p, "dir with spaces", p.uri());
                testBadCharsPath(vfs, tmp, "dir with spaces", tmp.uri());

                testBadCharsPath(vfs, p, "dir with back`tick", p.uri());
                testBadCharsPath(vfs, tmp, "dir with back`tick", tmp.uri());

                testBadCharsPath(vfs, p, "Ichiban no Takaramono `Yui final ver.`", p.uri());
                testBadCharsPath(vfs, tmp, "Ichiban no Takaramono `Yui final ver.`", tmp.uri());

                p = Arrays.stream(p.list())
                    .filter(pa -> UriUtils.getName(pa.uri()).startsWith("files"))
                    .findFirst()
                    .get();

                // subdir traversal
                testBadCharsFilePath(vfs, p, "file with back`tick.txt");

                // direct
                testBadCharsFilePath(vfs,
                    vfs.toPath(new URI("tar:gz:zip:tar:tar:bz2://" + fullPathToFnEnc + "!./tar%20In%20Zip%20In%20%60tar.tar!tarInZip.zip" + ZipFileSystem.ENC
                        + "other.tar.gz!other/files%20with%20bad%20chars")),
                    "file with back`tick.txt");
            }

        }

    }

    // This fails using the Apache virtual file system. It appears if you
    // get a FileObject by listing a directory, and that directory contains
    // a file with a square bracket in the name, then you try to get the
    // URI from that file object, it fails in the java URI conversion
    @Test
    public void testBadCharsName() throws Exception {
        try(final Vfs vfs = getVfs();) {
            final File otherTestFile = vfs.toFile(new URI("classpath:///other.tar"));
            assertTrue(otherTestFile.exists());

            final File otherTestDir = vfs.toFile(new URI("classpath:///other"));
            assertTrue(otherTestDir.exists());

            final String otherTestTarUriStr = "tar:" + otherTestFile.toURI().toString();
            final URI otherTestTarUri = new URI(otherTestTarUriStr);
            Path otherTestTar = vfs.toPath(otherTestTarUri);
            assertTrue(otherTestTar.exists());
            assertTrue(otherTestTar.isDirectory());
            assertEquals(1, otherTestTar.list().length);
            otherTestTar = otherTestTar.list()[0];

            final URI parentInTar = new URI(otherTestTarUriStr + "!other");

            testBadCharsDir(vfs, otherTestDir, "dir With Brackets[square]");
            testBadCharsPath(vfs, otherTestTar, "dir With Brackets[square]", parentInTar);

            testBadCharsDir(vfs, otherTestDir, "dir with spaces");
            testBadCharsPath(vfs, otherTestTar, "dir with spaces", parentInTar);

            testBadCharsDir(vfs, otherTestDir, "dir with back`tick");
            testBadCharsPath(vfs, otherTestTar, "dir with back`tick", parentInTar);

            testBadCharsDir(vfs, otherTestDir, "Ichiban no Takaramono `Yui final ver.`");
            testBadCharsPath(vfs, otherTestTar, "Ichiban no Takaramono `Yui final ver.`", parentInTar);

            final File dirWithFiles = new File(otherTestDir, "files with bad chars");
            testBadCharsFile(vfs, dirWithFiles, "file with back`tick.txt");
            testBadCharsFilePath(vfs, vfs.toPath(dirWithFiles.toURI()), "file with back`tick.txt");

        }
    }

    private void testBadCharsFile(final Vfs vfs, final File dirWithFiles, final String fileNameToTest) throws Exception {
        final Path dir = vfs.toPath(Paths.get(dirWithFiles.getAbsolutePath()).toUri());
        assertEquals(1, dir.list().length);
        testBadCharNameFile(vfs, dir.list()[0]);
        testBadCharNameFile(vfs, vfs.toPath(new File(dirWithFiles, fileNameToTest).toURI()));
    }

    private void testBadCharsFilePath(final Vfs vfs, final Path dirWithFiles, final String fileNameToTest) throws Exception {
        final Path dir = dirWithFiles;
        assertEquals(1, dir.list().length);
        testBadCharNameFile(vfs, dir.list()[0]);
        testBadCharNameFile(vfs, vfs.toPath(UriUtils.resolve(dir.uri(), fileNameToTest)));
    }

    private void testBadCharsPath(final Vfs vfs, final Path dir, final String fileNameToTestRaw, final URI parentUri) throws Exception {
        final String fileNameToTest = UriUtils.encodePath(fileNameToTestRaw);
        final Path indirectUnderTest = Arrays.stream(dir.list())
            .filter(p -> p.uri().toString().contains(fileNameToTest))
            .findAny()
            .get();

        testBadCharNameDir(vfs, indirectUnderTest);

        final URI directUnderTestUri = UriUtils.resolve(parentUri, fileNameToTestRaw);
        final Path directUnderTest = vfs.toPath(directUnderTestUri);
        assertTrue(directUnderTest.exists());
        testBadCharNameDir(vfs, directUnderTest);
    }

    private void testBadCharsDir(final Vfs vfs, final File otherTestDir, final String subdirToTest) throws Exception {

        final Path dirAbove = vfs.toPath(Paths.get(otherTestDir.getAbsolutePath()).toUri());
        final Path indirectUnderTest = Arrays.stream(dirAbove.list())
            .peek(p -> assertTrue(uncheck(() -> p.toFile()).exists()))
            .filter(p -> uncheck(() -> p.toFile()).getAbsolutePath().contains(subdirToTest))
            .findAny()
            .get();

        // this throws an IllegalArgumentException because of the square bracket. Fixed in
        // ApacheVfsFileSystem.uri()
        testBadCharNameDir(vfs, indirectUnderTest);

        final File underTestFile = new File(dirAbove.toFile(), subdirToTest);
        assertTrue(underTestFile.exists());
        final var jp = Paths.get(underTestFile.getAbsolutePath());
        testBadCharNameDir(vfs, vfs.toPath(jp.toUri()));
    }

    private void testBadCharNameDir(final Vfs vfs, final Path path) throws Exception {
        // this throws an IllegalArgumentException because of the square bracket. Fixed in
        // ApacheVfsFileSystem.uri()
        final URI indirectUnderTestUri = path.uri();
        assertNotNull(indirectUnderTestUri);
        Path p = vfs.toPath(indirectUnderTestUri);
        assertTrue(p.exists());
        assertTrue(p.isDirectory());
        assertEquals(1, p.list().length);
        assertEquals("test.txt", UriUtils.getName(p.list()[0].uri()));
        p = p.list()[0];
        try(var is = p.read();) {
            assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
        }
    }

    private void testBadCharNameFile(final Vfs vfs, final Path path) throws Exception {
        Path p = path;
        assertTrue(p.exists());
        assertFalse(p.isDirectory());
        try(var is = p.read();) {
            assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
        }

        final URI indirectUnderTestUri = path.uri();
        assertNotNull(indirectUnderTestUri);
        p = vfs.toPath(indirectUnderTestUri);
        assertTrue(p.exists());
        assertFalse(p.isDirectory());
        try(var is = p.read();) {
            assertEquals("Hello World", IOUtils.toString(is, Charset.defaultCharset()));
        }
    }
}
