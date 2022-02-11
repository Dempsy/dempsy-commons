package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.HexStringUtil;
import net.dempsy.vfs.internal.DempsyArchiveInputStream;
import net.dempsy.vfs.internal.LocalArchiveInputStream;
import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;

public abstract class CopiedArchiveFileSystem extends EncArchiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopiedArchiveFileSystem.class);

    protected static final File TMP;
    private static final LinkedList<URI> cachedExpanded = new LinkedList<>();
    private static final Map<URI, LinkedHashMap<String, FileDetails>> cachedEntries = new HashMap<>();
    private static final int DEFAULT_CACHE_LEN = 10;
    private static int cacheLen = DEFAULT_CACHE_LEN;

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

    protected CopiedArchiveFileSystem(final String enc) {
        super(enc);
    }

    public abstract LinkedHashMap<String, FileDetails> extract(final String scheme, final URI archiveUri, File destinationDirectory) throws IOException;

    public DempsyArchiveInputStream listingStream(final String scheme, final URI archiveUri) throws IOException {
        return createArchiveInputStream(scheme, archiveUri, false);
    }

    @Override
    public DempsyArchiveInputStream createArchiveInputStream(final String scheme, final URI archiveUri, final boolean listingOnly) throws IOException {

        if(listingOnly)
            return listingStream(scheme, archiveUri);

        final File dest = makeFileFromArchiveUri(archiveUri);
        if(!dest.exists() && !dest.mkdirs())
            throw new IllegalStateException("Couldn't find or create the temp dir \"" + dest.getAbsolutePath() + "\" for the url \"" + archiveUri + "\"");

        // we need to see if it's cached.
        final LinkedHashMap<String, FileDetails> results;
        if(cachedEntries.containsKey(archiveUri)) { // it's cached already.
            if(!dest.exists()) { // it's in the cache but actually not there. Recover from this error.
                removeCached(archiveUri);
                // if this throws an IOException then we need to still account for the cache entry
                try {
                    results = extract(scheme, archiveUri, dest);
                } catch(final IOException ioe) {
                    removeCached(archiveUri);
                    throw ioe;
                }
                addCached(archiveUri, results);
            } else
                results = getCached(archiveUri);
        } else {
            // even though we don't think it's there, make sure the path
            // is clear and everything is cleaned up.
            removeCached(archiveUri);
            try {
                results = extract(scheme, archiveUri, dest);
            } catch(final IOException ioe) {
                removeCached(archiveUri);
                throw ioe;
            }
            addCached(archiveUri, results);
        }

        return new LocalArchiveInputStream(dest, results);
    }

    public static void setMaxCachedArchives(final int maxCachedArchives) {
        cacheLen = maxCachedArchives;
    }

    /**
     * A helper method for Apache VFS2 supported archive file streams
     */
    protected static LinkedHashMap<String, FileDetails> copyArchiveInputStream(final URI archiveUri, final ArchiveInputStream tarIn, final File dest)
        throws IOException {
        final LinkedHashMap<String, FileDetails> ret = new LinkedHashMap<>();
        // tarIn is a TarArchiveInputStream
        for(ArchiveEntry tarEntry = tarIn.getNextEntry(); tarEntry != null; tarEntry = tarIn.getNextEntry()) {
            // create a file with the same name as the tarEntry
            final String entryName = tarEntry.getName();
            File destPath = new File(dest, entryName);
            final Date tmpDate = tarEntry.getLastModifiedDate();
            final long lastModTime = (tmpDate == null) ? -1 : tmpDate.getTime();
            final long size = tarEntry.getSize();

            if(ret.containsKey(entryName)) {
                String newEntryName;
                // find a new entry name.
                for(int index = 1; true; index++) {
                    newEntryName = entryName + ".COPY(" + index + ")";
                    if(!ret.containsKey(newEntryName))
                        break;
                }
                LOGGER.warn("The archive given by \"{}\" contains two entries with the same name: \"{}\". Remapping the later one to \"{}\"", archiveUri,
                    entryName, newEntryName);
                destPath = new File(dest, newEntryName);
                ret.put(newEntryName, new FileDetails(destPath, lastModTime, size));
            } else
                ret.put(entryName, new FileDetails(destPath, lastModTime, size));
            LOGGER.debug("working: {}", destPath.getCanonicalPath());
            if(tarEntry.isDirectory()) {
                destPath.mkdirs();
            } else {
                if(destPath.exists()) { // check if we want to overwrite.
                    final long existingSize = destPath.length();
                    if(existingSize == tarEntry.getSize()) {
                        LOGGER.debug("File \"{}\" already exists on the file system at \"{}\" ... skipping.", tarEntry.getName(),
                            destPath.getAbsolutePath());
                        continue;
                    } else {
                        LOGGER.debug("File \"{}\" already exists on the file system at \"{}\" but it's a differnt size ... overwriting.",
                            tarEntry.getName(),
                            destPath.getAbsolutePath());
                    }
                }
                final File parentDir = destPath.getParentFile();
                if(parentDir != null)
                    parentDir.mkdirs();
                destPath.createNewFile();
                try(final BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(destPath));) {
                    IOUtils.copy(tarIn, bout);
                }
            }
        }
        return ret;
    }

    protected static File makeFileFromArchiveUri(final URI archiveUri) {
        // generate a filename.
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        final String fname = HexStringUtil.bytesToHex(md.digest(archiveUri.toString().getBytes()));

        return new File(TMP, fname);
    }

    private static synchronized void removeCached(final URI archiveUri) {
        cachedEntries.remove(archiveUri);
        cachedExpanded.remove(archiveUri);
        final File dir = makeFileFromArchiveUri(archiveUri);
        if(dir.exists())
            FileUtils.deleteQuietly(dir);
    }

    private static synchronized LinkedHashMap<String, FileDetails> getCached(final URI archiveUri) {
        final LinkedHashMap<String, FileDetails> entry = cachedEntries.get(archiveUri);
        if(cachedExpanded.remove(archiveUri))
            cachedExpanded.add(archiveUri);
        return entry;
    }

    private static synchronized void addCached(final URI archiveUri, final LinkedHashMap<String, FileDetails> results) {
        // results can be null if the attempt to expand the archive failed.
        // In that case we might as well just assume there's nothing to add
        // but we did clean up any residue
        if(results != null) {
            cachedEntries.put(archiveUri, results);
            cachedExpanded.add(archiveUri);
            if(cachedExpanded.size() > cacheLen) {
                // remove the oldest one.
                final URI old = cachedExpanded.removeFirst();
                cachedEntries.remove(old);
                final File dir = makeFileFromArchiveUri(old);
                if(dir.exists())
                    FileUtils.deleteQuietly(dir);
            }
        }
    }
}
