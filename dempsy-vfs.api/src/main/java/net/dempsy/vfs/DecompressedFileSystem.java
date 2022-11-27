package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.HexStringUtil;

public class DecompressedFileSystem extends CompressedFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecompressedFileSystem.class);

    protected static final File TMP;

    static {
        final String v = System.getProperty("java.io.tmpdir");
        if(v == null) { // I have no idea on what system this can be null, if any.
            TMP = uncheck(() -> Files.createTempDirectory("TMP2").toFile());
        } else {
            final File tmpRoot = new File(v);
            TMP = new File(tmpRoot, "CFS2");
        }
        LOGGER.debug("Temp directory for {} is \"{}\"", CopiedArchiveFileSystem.class.getSimpleName(), TMP);
        if(!TMP.exists()) {
            if(!TMP.mkdirs())
                throw new IllegalStateException("Cannot find or make the system's temp directory");
        }

        Arrays.stream(TMP.listFiles()).forEach(f -> FileUtils.deleteQuietly(f));
    }

    private final Vfs vfs;
    private final String[] schemes;

    public DecompressedFileSystem(final CompressedFileSystem... fileSystems) throws IOException {
        vfs = new Vfs(fileSystems);

        schemes = Arrays.stream(fileSystems)
            .map(fs -> fs.supportedSchemes())
            .flatMap(ss -> Arrays.stream(ss))
            .collect(Collectors.toSet())
            .toArray(String[]::new);
    }

    @Override
    protected InputStream wrap(final Path path, final InputStream is) throws IOException {
        final File decompressedFile = makeFileFromArchiveUri(path.uri());
        if(!decompressedFile.exists()) {
            final CompressedFileSystem fs = (CompressedFileSystem)vfs.fileSystem(path.uri());
            try(var is2 = fs.wrap(path, is);
                var os = new BufferedOutputStream(new FileOutputStream(decompressedFile));) {
                IOUtils.copy(is2, os);
            }
        }
        return new BufferedInputStream(new FileInputStream(decompressedFile));
    }

    @Override
    protected OutputStream wrap(final Path path, final OutputStream os) throws IOException {
        final CompressedFileSystem fs = (CompressedFileSystem)vfs.fileSystem(path.uri());
        return fs.wrap(path, os);
    }

    @Override
    public String[] supportedSchemes() {
        return schemes;
    }

    protected static File makeFileFromArchiveUri(final URI archiveUri) {
        // generate a filename.
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        final String fname = HexStringUtil.bytesToHex(md.digest(archiveUri.toString().getBytes()));

        return new File(TMP, fname);
    }
}
