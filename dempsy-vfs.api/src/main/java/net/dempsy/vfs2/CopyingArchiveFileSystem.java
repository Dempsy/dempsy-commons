package net.dempsy.vfs2;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs2.internal.DempsyArchiveInputStream;
import net.dempsy.vfs2.internal.LocalArchiveInputStream;
import net.dempsy.vfs2.internal.LocalArchiveInputStream.FileDetails;
import net.dempsy.vfs2.internal.TempSpace;

public class CopyingArchiveFileSystem extends EncArchiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyingArchiveFileSystem.class);

    private static final String CTX_KEY = CopyingArchiveFileSystem.class.getSimpleName();

    private static final File TMP = TempSpace.get();
    private final EncArchiveFileSystem underlying;

    public CopyingArchiveFileSystem(final EncArchiveFileSystem underlying) {
        super(underlying.enc);
        this.underlying = underlying;
    }

    private static class Context implements QuietCloseable {
        private final File file;
        private boolean isExpanded;
        private LinkedHashMap<String, FileDetails> results = null;

        Context() {
            this.file = new File(TMP, UUID.randomUUID().toString());
            this.isExpanded = false;
        }

        @Override
        public void close() {
            LOGGER.debug("Cleaning {}", file);
            FileUtils.deleteQuietly(file);
        }
    }

    @Override
    public String[] supportedSchemes() {
        return underlying.supportedSchemes();
    }

    @Override
    protected void setVfs(final Vfs vfs) {
        super.setVfs(vfs);
        underlying.setVfs(vfs);
    }

    @Override
    protected synchronized DempsyArchiveInputStream createArchiveInputStream(final String scheme, final URI archiveUri, final boolean listingOnly,
        final OpContext ctx) throws IOException {

        final Path archivePath = ctx.toPath(archiveUri);
        final Context c = archivePath.getContext(CTX_KEY, () -> new Context());

        // expand the tar file
        if(!c.isExpanded) {
            ctx.toPath(c.file.toURI()).mkdirs();
            if(!c.file.exists() || !c.file.isDirectory())
                throw new IllegalStateException("The temp directory at \"" + c.file + "\" couldn't be created or isn't a directory");

            try(DempsyArchiveInputStream tarIn = underlying.createArchiveInputStream(scheme, archiveUri, listingOnly, ctx);) {
                c.results = copyArchiveInputStream(archiveUri, tarIn, c.file);
            }
            c.isExpanded = true;
        }

        return new LocalArchiveInputStream(c.file, c.results);
    }

    protected static LinkedHashMap<String, FileDetails> copyArchiveInputStream(final URI archiveUri, final ArchiveInputStream tarIn, final File dest)
        throws IOException {

        LOGGER.debug("Extracting {} to {}", archiveUri, dest);

        final LinkedHashMap<String, FileDetails> ret = new LinkedHashMap<>();
        // tarIn is a TarArchiveInputStream
        for(ArchiveEntry tarEntry = tarIn.getNextEntry(); tarEntry != null; tarEntry = tarIn.getNextEntry()) {
            // create a file with the same name as the tarEntry
            final String entryName = tarEntry.getName();
            final Date tmpDate = tarEntry.getLastModifiedDate();
            final long lastModTime = (tmpDate == null) ? -1 : tmpDate.getTime();
            final long size = tarEntry.getSize();
            final boolean isDir = tarEntry.isDirectory();

            File destPath = isDir ? null : new File(dest, UUID.randomUUID().toString());

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
                destPath = isDir ? null : new File(dest, newEntryName);
                ret.put(newEntryName, new FileDetails(destPath, lastModTime, size, isDir));
            } else
                ret.put(entryName, new FileDetails(destPath, lastModTime, size, isDir));
            LOGGER.debug("working: {}", isDir ? entryName : destPath.getCanonicalPath());
            if(!isDir) {
                // if(destPath.exists()) { // check if we want to overwrite.
                // final long existingSize = destPath.length();
                // if(existingSize == tarEntry.getSize()) {
                // LOGGER.debug("File \"{}\" already exists on the file system at \"{}\" ... skipping.", tarEntry.getName(),
                // destPath.getAbsolutePath());
                // continue;
                // } else {
                // LOGGER.debug("File \"{}\" already exists on the file system at \"{}\" but it's a differnt size ... overwriting.",
                // tarEntry.getName(),
                // destPath.getAbsolutePath());
                // }
                // }
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
