package net.dempsy.vfs;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.LinkedHashMap;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.vfs.internal.CopiedArchiveFileSystemCache;
import net.dempsy.vfs.internal.DempsyArchiveInputStream;
import net.dempsy.vfs.internal.LocalArchiveInputStream;
import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;

public abstract class CopiedArchiveFileSystem extends EncArchiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopiedArchiveFileSystem.class);

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

        try(var vfscontext = vfs.context();) {
            final var cache = getCache(vfscontext);

            final File dest = cache.makeFileFromArchiveUri(archiveUri);
            if(!dest.exists() && !dest.mkdirs())
                throw new IllegalStateException("Couldn't find or create the temp dir \"" + dest.getAbsolutePath() + "\" for the url \"" + archiveUri + "\"");

            // we need to see if it's cached.
            final LinkedHashMap<String, FileDetails> results;
            if(cache.contains(archiveUri)) { // it's cached already.
                if(!dest.exists()) { // it's in the cache but actually not there. Recover from this error.
                    cache.remove(archiveUri);
                    // if this throws an IOException then we need to still account for the cache entry
                    try {
                        results = extract(scheme, archiveUri, dest);
                    } catch(final IOException ioe) {
                        cache.remove(archiveUri);
                        throw ioe;
                    }
                    cache.add(archiveUri, results);
                } else
                    results = cache.get(archiveUri);
            } else {
                // even though we don't think it's there, make sure the path
                // is clear and everything is cleaned up.
                cache.remove(archiveUri);
                try {
                    results = extract(scheme, archiveUri, dest);
                } catch(final IOException ioe) {
                    cache.remove(archiveUri);
                    throw ioe;
                }
                cache.add(archiveUri, results);
            }

            return new LocalArchiveInputStream(dest, results);
        }
    }

    protected CopiedArchiveFileSystemCache getCache(final Vfs.Context vfscontext) {
        return vfscontext.get("CopiedArchiveFileSystemCache", () -> new CopiedArchiveFileSystemCache());
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
}
