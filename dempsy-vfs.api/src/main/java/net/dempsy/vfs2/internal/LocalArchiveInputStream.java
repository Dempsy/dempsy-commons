package net.dempsy.vfs2.internal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class LocalArchiveInputStream extends DempsyArchiveInputStream {

    private final ArrayList<LocalArchiveEntry> files;
    private final Iterator<LocalArchiveEntry> iter;

    private LocalArchiveEntry cur = null;
    private InputStream curIs = null;

    public static class FileDetails {
        public final File file;
        public final long lastModified;
        public final long length;
        public final boolean isDir;

        public FileDetails(final File file, final long lastModified, final long length, final boolean isDir) {
            this.file = file;
            this.lastModified = lastModified;
            this.length = length;
            this.isDir = isDir;
        }
    }

    public LocalArchiveInputStream(final File dest, final LinkedHashMap<String, FileDetails> state) {
        files = new ArrayList<>();
        state.forEach((name, file) -> files.add(new LocalArchiveEntry(name, file)));
        iter = files.iterator();
    }

    @Override
    public DempsyArchiveEntry getNextEntry() throws IOException {
        cur = iter.hasNext() ? iter.next() : null;
        if(curIs != null)
            curIs.close();
        curIs = (cur == null || cur.isDirectory()) ? null : new BufferedInputStream(new FileInputStream(cur.details.file));
        return cur;
    }

    @Override
    public void close() throws IOException {
        if(curIs != null)
            curIs.close();
    }

    @Override
    public int read(final byte b[], final int off, final int len) throws IOException {
        if(curIs == null) {
            if(cur != null && cur.isDirectory())
                throw new IllegalStateException("Cannot read bytes from a directory at \"" + cur.getName() + "\"");
            else
                throw new NullPointerException("");
        }
        return curIs.read(b, off, len);
    }

    private static class LocalArchiveEntry implements DempsyArchiveEntry {
        final private String name;
        final private FileDetails details;

        private LocalArchiveEntry(final String name, final FileDetails cur) {
            this.name = name;
            this.details = cur;
            if(details == null)
                throw new NullPointerException("Cannot have null FileDetails in a LocalArchiveEntry");
            if(details.file == null && !details.isDir)
                throw new IllegalArgumentException("A FileDetails with a missing file MUST be a directory.");
        }

        @Override
        public boolean isDirectory() {
            return details.isDir;
        }

        @Override
        public long getSize() {
            if(details.isDir)
                return 0;
            return details.length < 0 ? details.file.length() : details.length;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Date getLastModifiedDate() {
            return(details.lastModified < 0 ? new Date(details.file.lastModified()) : new Date(details.lastModified));
        }

        @Override
        public File direct() {
            return details.file;
        }
    }
}
