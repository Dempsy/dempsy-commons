package net.dempsy.vfs.internal;

import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.HexStringUtil;
import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs.CopiedArchiveFileSystem;
import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;

public class CopiedArchiveFileSystemCache implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopiedArchiveFileSystem.class);

    protected static final File TMP;
    private final LinkedList<URI> cachedExpanded = new LinkedList<>();
    private final Map<URI, LinkedHashMap<String, FileDetails>> cachedEntries = new HashMap<>();
    // private final int DEFAULT_CACHE_LEN = 10;
    // private final int cacheLen = DEFAULT_CACHE_LEN;

    static {
        final String v = System.getProperty("java.io.tmpdir");
        if(v == null) { // I have no idea on what system this can be null, if any.
            TMP = uncheck(() -> Files.createTempDirectory("TMP").toFile());
        } else {
            final File tmpRoot = new File(v);
            TMP = new File(tmpRoot, "CFS");
        }
        LOGGER.debug("Temp directory for {} is \"{}\"", CopiedArchiveFileSystem.class.getSimpleName(), TMP);
        if(!TMP.exists()) {
            if(!TMP.mkdirs())
                throw new IllegalStateException("Cannot find or make the system's temp directory");
        }

        Arrays.stream(TMP.listFiles()).forEach(f -> FileUtils.deleteQuietly(f));
    }

    @Override
    public synchronized void close() {
        cachedExpanded.forEach(u -> remove(u));
    }

    public File makeFileFromArchiveUri(final URI archiveUri) {
        // generate a filename.
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        final String fname = HexStringUtil.bytesToHex(md.digest(archiveUri.toString().getBytes()));

        return new File(TMP, fname);
    }

    public boolean contains(final URI archiveUri) {
        return cachedEntries.containsKey(archiveUri);
    }

    public synchronized void remove(final URI archiveUri) {
        cachedEntries.remove(archiveUri);
        cachedExpanded.remove(archiveUri);
        final File dir = makeFileFromArchiveUri(archiveUri);
        if(dir.exists())
            FileUtils.deleteQuietly(dir);
    }

    public synchronized LinkedHashMap<String, FileDetails> get(final URI archiveUri) {
        final LinkedHashMap<String, FileDetails> entry = cachedEntries.get(archiveUri);
        if(cachedExpanded.remove(archiveUri))
            cachedExpanded.add(archiveUri);
        return entry;
    }

    public synchronized void add(final URI archiveUri, final LinkedHashMap<String, FileDetails> results) {
        // results can be null if the attempt to expand the archive failed.
        // In that case we might as well just assume there's nothing to add
        // but we did clean up any residue
        if(results != null) {
            cachedEntries.put(archiveUri, results);
            cachedExpanded.add(archiveUri);
            // if(cachedExpanded.size() > cacheLen) {
            // // remove the oldest one.
            // final URI old = cachedExpanded.removeFirst();
            // cachedEntries.remove(old);
            // final File dir = makeFileFromArchiveUri(old);
            // if(dir.exists())
            // FileUtils.deleteQuietly(dir);
            // }
        }
    }
}
