package net.dempsy.vfs.internal;

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

        public FileDetails(final File file, final long lastModified, final long length) {
            this.file = file;
            this.lastModified = lastModified;
            this.length = length;
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
        curIs = (cur == null || cur.isDirectory()) ? null : new BufferedInputStream(new FileInputStream(cur.file));
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
        final private File file;
        final private FileDetails details;

        private LocalArchiveEntry(final String name, final File cur) {
            this.name = name;
            this.file = cur;
            this.details = null;
        }

        private LocalArchiveEntry(final String name, final FileDetails cur) {
            this.name = name;
            this.file = cur.file;
            this.details = cur;
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public long getSize() {
            return details == null ? file.length() : (details.length < 0 ? file.length() : details.length);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Date getLastModifiedDate() {
            return details == null ? new Date(file.lastModified())
                : (details.lastModified < 0 ? new Date(file.lastModified()) : new Date(details.lastModified));
        }

        @Override
        public File direct() {
            return file;
        }
    }
}
